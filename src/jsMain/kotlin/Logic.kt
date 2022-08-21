import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromDynamic
import minima.*
import ui.eltooScriptAddress
import ui.eltooScriptCoins
import ui.multisigScriptAddress
import ui.multisigScriptBalances
import kotlin.random.Random

fun triggerScript(triggerSig1: String, triggerSig2: String) =
  "RETURN MULTISIG(2 $triggerSig1 $triggerSig2)"

fun eltooScript(blockDiff: Int = 256, updateSig1: String, updateSig2: String, settleSig1: String, settleSig2: String) = """
LET st=STATE(99)
LET ps=PREVSTATE(99)
IF st EQ ps AND @COINAGE GT $blockDiff AND MULTISIG(2 $settleSig1 $settleSig2) THEN
RETURN TRUE
ELSEIF st GT ps AND MULTISIG(2 $updateSig1 $updateSig2) THEN
RETURN TRUE
ENDIF
"""

val balances = mutableStateListOf<Balance>()
var blockNumber by mutableStateOf(0)

fun newTxId() = Random.nextInt(1_000_000_000)

fun init() {
//  Minima.debug = true
//  MDS.logging = true
  MDS.init { msg: dynamic ->
    val event = msg.event
    when(event) {
      "inited" -> {
        if (MDS.logging) console.log("Connected to Minima.")
        scope.launch {
          balances.addAll(getBalances())
        }
      }
      "NEWBLOCK" -> {
        blockNumber = (msg.data.txpow.header.block as String).toInt()
        if (multisigScriptAddress.isNotEmpty() && multisigScriptBalances.none { it.unconfirmed > ZERO }) {
          scope.launch {
            multisigScriptBalances.clear()
            multisigScriptBalances.addAll(getBalances(multisigScriptAddress))
          }
        }
        if (eltooScriptAddress.isNotEmpty() && eltooScriptCoins.isEmpty()) {
          scope.launch {
            eltooScriptCoins.clear()
            eltooScriptCoins.addAll(getCoins(address = eltooScriptAddress, sendable = false))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun getBalances(address: String? = null): List<Balance> {
  val balance = MDS.cmd("balance ${address?.let{"address:$address "} ?:""}")
  val balances = balance.response as Array<dynamic>
  return balances.map {
    try {
      Balance(
        token = if (it.token == "Minima") null else json.decodeFromDynamic<TokenDescriptor>(it.token),
        tokenid = it.tokenid,
        confirmed = json.decodeFromDynamic(it.confirmed),
        unconfirmed = json.decodeFromDynamic(it.unconfirmed),
        sendable = json.decodeFromDynamic(it.sendable),
        coins = (it.coins as String).toInt()
      )
    } catch (e: Exception) {
      console.log("Exception mapping balance", it, e)
      throw e
    }
  }
}

suspend fun newAddress(): String {
  val newaddress = MDS.cmd("newaddress")
  return newaddress.response.miniaddress
}

suspend fun newKey(): String {
  val keys = MDS.cmd("keys action:new")
  return keys.response.publickey as String
}

suspend fun deployScript(text: String): String {
  val newscript = MDS.cmd("""newscript script:"$text" track:true""")
  return newscript.response.address
}

suspend fun getCoins(tokenId: String? = null, address: String? = null, sendable: Boolean): List<Coin> {
  val coinSimple = MDS.cmd("coins ${tokenId?.let{"tokenid:$tokenId "} ?:""} ${address?.let{"address:$address "} ?:""}sendable:$sendable")
  val coins = json.decodeFromDynamic<Array<Coin>>(coinSimple.response)
  return coins.sortedBy { it.amount }
}

suspend fun send(toAddress: String, amount: BigDecimal, tokenId: String) {
  val txnId = newTxId()
  val inputs = mutableListOf<Coin>()
  val outputs = mutableListOf<Output>()
  coverShortage(tokenId, amount, inputs, outputs)
  
  val txncreator = "txncreate id:$txnId;" +
    inputs.map{ "txninput id:$txnId coinid:${it.coinid};"}.joinToString("") +
    "txnoutput id:$txnId amount:${amount.toPlainString()} address:$toAddress tokenid:$tokenId;" +
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} address:${it.address} tokenid:${it.token};"}.joinToString("") +
    "txnsign id:$txnId publickey:auto;" +
    "txnpost id:$txnId auto:true;" +
    "txndelete id:$txnId;"
  
  val result = MDS.cmd(txncreator)
}

data class Output(val address: String, val amount: BigDecimal, val token: String)

suspend fun coverShortage(tokenId: String, shortage: BigDecimal, inputs: MutableList<Coin>, outputs: MutableList<Output>) {
  
  val coins = getCoins(tokenId = tokenId, sendable = true).ofAtLeast(shortage)
  coins.forEach { inputs.add(it) }
  val change = coins.sumOf { it.tokenamount ?: it.amount } - shortage
  if (change > ZERO) outputs.add(Output(newAddress(), change, tokenId))
}

fun List<Coin>.ofAtLeast(amount: BigDecimal): List<Coin> {
  return firstOrNull { (it.tokenamount ?: it.amount) >= amount }
    ?.let{ listOf(it) }
    ?: (listOf(last()) + take(size-1).ofAtLeast(amount - (last().tokenamount ?: last().amount)))
}

fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal) = fold(ZERO) { acc, item -> acc + selector(item) }

suspend fun fundingTx(toAddress: String, amount: BigDecimal, tokenId: String): Pair<Int, dynamic> {
  val txnId = newTxId()
  val inputs = mutableListOf<Coin>()
  val outputs = mutableListOf<Output>()
  coverShortage(tokenId, amount, inputs, outputs)
  
  val txncreator = "txncreate id:$txnId;" +
    inputs.map{ "txninput id:$txnId coinid:${it.coinid};"}.joinToString("") +
    "txnoutput id:$txnId amount:${amount.toPlainString()} address:$toAddress tokenid:$tokenId;" +
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} address:${it.address} tokenid:${it.token};"}.joinToString("")
  
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txn = result.last()
  console.log("funding tx", txn.response)
  return txnId to txn.response
}

suspend fun signTx(txnId: Int, key: String): dynamic {
  val txncreator = "txnsign id:$txnId publickey:$key;"
  val result = MDS.cmd(txncreator)
  console.log("import", result.status)
  return result
}

suspend fun exportTx(txnId: Int): String {
  val txncreator = "txnexport id:$txnId;"
  val result = MDS.cmd(txncreator)
  console.log("export", result.response.data)
  return result.response.data as String
}

suspend fun importSignExportTx(data: String, key: String): Pair<Int, String> {
  val id = newTxId()
  importTx(id, data)
  signTx(id, key)
  return id to exportTx(id)
}

suspend fun importTx(txnId: Int, data: String): dynamic {
  val txncreator = "txncreate id:$txnId;" +
    "txnimport id:$txnId data:$data;"
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnimport = result.find{it.command == "txnimport"}
  console.log("import", txnimport.status)
  return txnimport
}

suspend fun signAndPost(txnId: Int, key: String): Boolean {
  val txncreator = "txnsign id:$txnId publickey:$key;" +
//    "txnbasics id:$txnId;" +
    "txnpost id:$txnId auto:true;" +
    "txndelete id:$txnId;"
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnpost = result.find{it.command == "txnpost"}
  console.log("txnpost status", txnpost.status)
  return txnpost.status as Boolean
}

suspend fun signFloatingTx(myKey: String, sourceScriptAddress: String, targetAddress: String, tx: dynamic, states: Map<Int, String> = emptyMap()): Pair<Int, dynamic> {
  console.log("outputs", tx.transaction.outputs)
  val output = (tx.transaction.outputs as Array<dynamic>).find { it.address == sourceScriptAddress }
  
  val txnId = newTxId()
  val txncreator = "txncreate id:$txnId;" +
    "txninput id:$txnId address:${output.address} amount:${output.amount} tokenid:${output.tokenid} floating:true;" +
    states.mapNotNull { (index, value) -> value.takeUnless { it.isEmpty() }?.let {"txnstate id:$txnId port:$index value:$value;" } }.joinToString("") +
    "txnoutput id:$txnId amount:${output.amount} address:$targetAddress;" +
    "txnsign id:$txnId publickey:$myKey;"
  
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnoutput = result.find{it.command == "txnoutput"}
  return txnId to txnoutput.response
}

suspend fun post(txnId: Int): dynamic {
  val txncreator = "txnpost id:$txnId auto:true;"
  val result = MDS.cmd(txncreator)
  return result.response
}
