import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import minima.*
import ui.*
import kotlin.random.Random

data class ChannelState(
  val id: Int,
  val sequenceNumber: Int = 0,
  val status: String,
)

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
          createDB()
        }
      }
      "NEWBLOCK" -> {
        blockNumber = (msg.data.txpow.header.block as String).toInt()
        if (multisigScriptAddress.isNotEmpty()) {
          scope.launch {
            multisigScriptBalances.clear()
            multisigScriptBalances.addAll(getBalances(multisigScriptAddress))
          }
        }
        if (eltooScriptAddress.isNotEmpty()) {
          scope.launch {
            eltooScriptCoins.clear()
            eltooScriptCoins.addAll(getCoins(address = eltooScriptAddress, sendable = false))
          }
        }
      }
    }
  }
}

suspend fun send(toAddress: String, amount: BigDecimal, tokenId: String): Boolean {
  val txnId = newTxId()
  val inputs = mutableListOf<Coin>()
  val outputs = mutableListOf<Output>()
  coverShortage(tokenId, amount, inputs, outputs)
  
  val txncreator = "txncreate id:$txnId;" +
    inputs.map{ "txninput id:$txnId coinid:${it.coinid};"}.joinToString("") +
    "txnoutput id:$txnId amount:${amount.toPlainString()} address:$toAddress tokenid:$tokenId;" +
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} address:${it.address} tokenid:${it.tokenid};"}.joinToString("") +
    "txnsign id:$txnId publickey:auto;" +
    "txnpost id:$txnId auto:true;" +
    "txndelete id:$txnId;"
  
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnpost = result.find{it.command == "txnpost"}
  console.log("send", txnpost.status)
  return txnpost.status
}

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
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} address:${it.address} tokenid:${it.tokenid};"}.joinToString("")
  
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txn = result.last()
  return txnId to txn.response
}

suspend fun signAndExportTx(id: Int, key: String): String {
  signTx(id, key)
  return exportTx(id)
}

suspend fun signFloatingTx(myKey: String, sourceScriptAddress: String, targetAddress: String, tx: dynamic, states: Map<Int, String> = emptyMap()): Pair<Int, dynamic> {
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

suspend fun requestViaChannel(
  amount: BigDecimal,
  channelBalance: Pair<BigDecimal, BigDecimal>,
  myAddress: String,
  myUpdateKey: String,
  mySettleKey: String,
  counterPartyAddress: String,
  currentSettlementTx: JsonObject,
  channelKey: String
) = sendViaChannel(-amount, channelBalance, myAddress, myUpdateKey, mySettleKey, counterPartyAddress, currentSettlementTx, channelKey)

suspend fun sendViaChannel(
  amount: BigDecimal,
  channelBalance: Pair<BigDecimal, BigDecimal>,
  myAddress: String,
  myUpdateKey: String,
  mySettleKey: String,
  counterPartyAddress: String,
  currentSettlementTx: JsonObject,
  channelKey: String
) {
  val input = json.decodeFromJsonElement<Input>(currentSettlementTx["inputs"]!!.jsonArray.first())
  val state = currentSettlementTx["state"]!!.jsonArray.find{ it.jsonObject["port"]!!.jsonPrimitive.int == 99 }!!.jsonObject["data"]!!.jsonPrimitive.content
  val updateTxnId = newTxId()
  val updatetxncreator = "txncreate id:$updateTxnId;" +
    "txninput id:$updateTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenid} floating:true;" +
    "txnstate id:$updateTxnId port:99 value:${state.toInt() + 1};" +
    "txnoutput id:$updateTxnId amount:${input.amount} address:${input.address};" +
    "txnsign id:$updateTxnId publickey:$myUpdateKey;" +
    "txnexport id:$updateTxnId;"
  val updateTxn = (MDS.cmd(updatetxncreator) as Array<dynamic>).last()
  val settleTxnId = newTxId()
  val settletxncreator = "txncreate id:$settleTxnId;" +
    "txninput id:$settleTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenid} floating:true;" +
    "txnstate id:$settleTxnId port:99 value:${state.toInt() + 1};" +
    "txnoutput id:$settleTxnId amount:${(channelBalance.first - amount).toPlainString()} address:$myAddress;" +
    "txnoutput id:$settleTxnId amount:${(channelBalance.second + amount).toPlainString()} address:$counterPartyAddress;" +
    "txnsign id:$settleTxnId publickey:$mySettleKey;" +
    "txnexport id:$settleTxnId;"
  val settleTxn = (MDS.cmd(settletxncreator) as Array<dynamic>).last()
  
  publish(channelKey, listOf(if(amount > ZERO) "TXN_UPDATE" else "TXN_REQUEST", updateTxn.response.data, settleTxn.response.data).joinToString(";"))
}

suspend fun channelUpdate(isAck: Boolean, updateTx: String, settleTx: String, myUpdateKey: String, mySettleKey: String, channelKey: String): Pair<Int, Pair<Int, JsonObject>> {
  console.log("Updating channel")
  val updateTxnId = newTxId()
  importTx(updateTxnId, updateTx)
  val settleTxnId = newTxId()
  val importedSettleTx = importTx(settleTxnId, settleTx)
  
  if (!isAck) {
    val signedUpdateTx = signAndExportTx(updateTxnId, myUpdateKey)
    val signedSettleTx = signAndExportTx(settleTxnId, mySettleKey)
    publish(channelKey, listOf("TXN_UPDATE_ACK", signedUpdateTx, signedSettleTx).joinToString(";"))
  }
  
  return updateTxnId to (settleTxnId to json.decodeFromJsonElement(importedSettleTx))
}

suspend fun prepareFundChannel(
  otherTriggerKey: String,
  otherUpdateKey: String,
  otherSettleKey: String,
  timeLock: Int,
  myTriggerKey: String,
  myUpdateKey: String,
  mySettleKey: String,
  exportedTriggerTx: String,
  exportedSettlementTx: String,
  amount: BigDecimal
): Int {
  MDS.sql("""INSERT INTO channel(
      status, sequence_number, my_balance, other_balance,
      my_trigger_key, my_update_key, my_settle_key,
      other_trigger_key, other_update_key, other_settle_key,
      trigger_tx, settle_tx
    ) VALUES (
      'OFFERED', 0, ${amount.toPlainString()}, 0,
      '$myTriggerKey', '$myUpdateKey', '$mySettleKey',
      '$otherTriggerKey', '$otherUpdateKey', '$otherSettleKey',
      '$exportedTriggerTx', '$exportedSettlementTx'
    );
  """)
  val sql = MDS.sql("SELECT IDENTITY() as ID;")
  val results = sql.rows as Array<dynamic>

  publish(
    channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
    listOf(timeLock, myTriggerKey, myUpdateKey, mySettleKey, exportedTriggerTx, exportedSettlementTx).joinToString(";")
  )
  return (results[0]["ID"] as String).toInt()
}

suspend fun commitFundChannel(channelId: Int, txnId: Int, key: String): Boolean {
  val txncreator = "txnsign id:$txnId publickey:$key;" +
    "txnpost id:$txnId auto:true;" +
    "txndelete id:$txnId;"
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val status = result.find{it.command == "txnpost"}.status
  console.log("txnpost status", status)
  if (status == true) {
    MDS.sql("""UPDATE channel SET
      updated_at = NOW(), status = 'OPEN'
      WHERE id = $channelId;""")
  }
  return status as Boolean
}