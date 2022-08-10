import androidx.compose.runtime.mutableStateListOf
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromDynamic
import minima.*
import kotlin.random.Random

fun script(blockDiff: Int, updateSig1: String, updateSig2: String, settleSig1: String, settleSig2: String) =
  "RETURN MULTISGI(2 $updateSig1 $updateSig2) OR ( @BLKDIFF GTE $blockDiff AND MULTISIG(2 $settleSig1 $settleSig2) )"

val balances = mutableStateListOf<Balance>()

fun newTxId() = Random.nextInt(1_000_000_000)

fun init() {
//  Minima.debug = true
  MDS.logging = true
  MDS.init { msg: dynamic ->
    val event = msg.event
    when {
      event == "inited" -> {
        if (MDS.logging) console.log("Connected to Minima.")
        scope.launch {
          balances.addAll(getBalances())
        }
      }
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun getBalances(): List<Balance> {
  val balance = MDS.cmd("balance")
  val balances = balance.response as Array<dynamic>
  return balances.map {
    Balance(
      token = if (it.token == "Minima") null else json.decodeFromDynamic<TokenDescriptor>(it.token),
      tokenid = it.tokenid,
      confirmed = json.decodeFromDynamic(it.confirmed),
      unconfirmed = json.decodeFromDynamic(it.confirmed),
      sendable = json.decodeFromDynamic(it.confirmed),
    )
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

suspend fun getCoins(tokenId: String): List<Coin> {
  val coinSimple = MDS.cmd("coins tokenid:$tokenId sendable:true")
  val coins = json.decodeFromDynamic<Array<Coin>>(coinSimple.response)
  return coins.sortedBy { it.amount }
}

suspend fun post(toAddress: String, amount: BigDecimal, tokenId: String) {
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
  
  val coins = getCoins(tokenId).ofAtLeast(shortage)
  coins.forEach { inputs.add(it) }
  val change = coins.sumOf { it.amount } - shortage
  if (change > BigDecimal.ZERO) outputs.add(Output(newAddress(), change, tokenId))
}

fun List<Coin>.ofAtLeast(amount: BigDecimal): List<Coin> {
  return firstOrNull { it.amount >= amount }
    ?.let{ listOf(it) }
    ?: (listOf(last()) + take(size-1).ofAtLeast(amount - last().amount))
}

fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal) = fold(BigDecimal.ZERO) { acc, item -> acc + selector(item) }

suspend fun exportTx(toAddress: String, amount: BigDecimal, tokenId: String): String {
  val txnId = newTxId()
  val inputs = mutableListOf<Coin>()
  val outputs = mutableListOf<Output>()
  coverShortage(tokenId, amount, inputs, outputs)
  
  val txncreator = "txncreate id:$txnId;" +
    inputs.map{ "txninput id:$txnId coinid:${it.coinid};"}.joinToString("") +
    "txnoutput id:$txnId amount:${amount.toPlainString()} address:$toAddress tokenid:$tokenId;" +
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} address:${it.address} tokenid:${it.token};"}.joinToString("") +
    "txnexport id:$txnId;"
  
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnexport = result.find{it.command == "txnexport"}
  console.log("export", txnexport.response.data)
  return txnexport.response.data as String
}

suspend fun importTx(tx: String) {
  val txnId = newTxId()
  
  val txncreator = "txncreate id:$txnId;" +
    "txnimport id:$txnId data:$tx"
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnimport = result.find{it.command == "txnimport"}
  console.log("import", txnimport.message)
}