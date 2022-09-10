import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import minima.*
import minima.State
import kotlin.js.Date
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

var multisigScriptAddress by mutableStateOf("")
var eltooScriptAddress by mutableStateOf("")
val multisigScriptBalances = mutableStateListOf<Balance>()
val eltooScriptCoins = mutableStateMapOf<String, List<Coin>>()

fun init() {
//  Minima.debug = true
//  MDS.logging = true
  MDS.init { msg: dynamic ->
    val event = msg.event
    when(event) {
      "inited" -> {
        if (MDS.logging) console.log("Connected to Minima.")
        scope.launch {
          blockNumber = getBlockNumber()
          balances.addAll(getBalances())
          createDB()
          getChannels(status = "OPEN").forEach {
            subscribe(channelKey(it.myTriggerKey, it.myUpdateKey, it.mySettleKey), from = it.updatedAt).onEach { msg ->
              console.log("tx msg", msg)
              val splits = msg.split(";")
              if (splits[0].startsWith("TXN_UPDATE")) {
                it.update(splits[0].endsWith("_ACK"), updateTx = splits[1], settleTx = splits[2])
              }
            }.onCompletion {
              console.log("completed")
            }.launchIn(scope)
          }
        }
      }
      "NEWBLOCK" -> {
        blockNumber = (msg.data.txpow.header.block as String).toInt()
        if (multisigScriptAddress.isNotEmpty()) {
          scope.launch {
            val newBalances = getBalances(multisigScriptAddress)
            if (newBalances.any { it.unconfirmed > ZERO || it.confirmed > ZERO } && multisigScriptBalances.none { it.unconfirmed > ZERO || it.confirmed > ZERO }) setChannelOpen(multisigScriptAddress)
            multisigScriptBalances.clear()
            multisigScriptBalances.addAll(newBalances)
          }
        }
        if (eltooScriptAddress.isNotEmpty()) {
          scope.launch {
            eltooScriptCoins.put(eltooScriptAddress, getCoins(address = eltooScriptAddress))
          }
        }
      }
    }
  }
}

suspend fun send(toAddress: String, amount: BigDecimal, tokenId: String): Boolean {
  val txnId = newTxId()
  val (inputs, outputs) = withChange(tokenId, amount)
  
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

suspend fun withChange(tokenId: String, amount: BigDecimal): Pair<List<Coin>, List<Output>> {
  val inputs = mutableListOf<Coin>()
  val outputs = mutableListOf<Output>()
  val coins = getCoins(tokenId = tokenId, sendable = true).ofAtLeast(amount)
  coins.forEach { inputs.add(it) }
  val change = coins.sumOf { it.tokenamount ?: it.amount } - amount
  if (change > ZERO) outputs.add(Output(newAddress(), change, tokenId))
  return inputs to outputs
}

fun List<Coin>.ofAtLeast(amount: BigDecimal): List<Coin> {
  return firstOrNull { (it.tokenamount ?: it.amount) >= amount }
    ?.let{ listOf(it) }
    ?: (listOf(last()) + take(size-1).ofAtLeast(amount - (last().tokenamount ?: last().amount)))
}

fun <T> Iterable<T>.sumOf(selector: (T) -> BigDecimal) = fold(ZERO) { acc, item -> acc + selector(item) }

suspend fun fundingTx(toAddress: String, amount: BigDecimal, tokenId: String): Pair<Int, dynamic> {
  val txnId = newTxId()
  val (inputs, outputs) = withChange(tokenId, amount)
  
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

suspend fun signFloatingTx(
  myKey: String,
  sourceScriptAddress: String,
  targetAddress: String,
  tx: dynamic,
  states: Map<Int, String> = emptyMap()
): Pair<Int, dynamic> {
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

suspend fun requestViaChannel(amount: BigDecimal, channel: ChannelState) = sendViaChannel(-amount, channel)

suspend fun sendViaChannel(amount: BigDecimal, channel: ChannelState) {
  val currentSettlementTx = importTx(newTxId(), channel.settlementTx)
  val input = json.decodeFromJsonElement<Input>(currentSettlementTx["inputs"]!!.jsonArray.first())
  val state = currentSettlementTx["state"]!!.jsonArray.find{ it.jsonObject["port"]!!.jsonPrimitive.int == 99 }!!.jsonObject["data"]!!.jsonPrimitive.content
  val updateTxnId = newTxId()
  val updatetxncreator = "txncreate id:$updateTxnId;" +
    "txninput id:$updateTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenid} floating:true;" +
    "txnstate id:$updateTxnId port:99 value:${state.toInt() + 1};" +
    "txnoutput id:$updateTxnId amount:${input.amount} address:${input.address};" +
    "txnsign id:$updateTxnId publickey:${channel.myUpdateKey};" +
    "txnexport id:$updateTxnId;"
  val updateTxn = (MDS.cmd(updatetxncreator) as Array<dynamic>).last()
  val settleTxnId = newTxId()
  val settletxncreator = "txncreate id:$settleTxnId;" +
    "txninput id:$settleTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenid} floating:true;" +
    "txnstate id:$settleTxnId port:99 value:${state.toInt() + 1};" +
    (if(channel.myBalance - amount > ZERO)
      "txnoutput id:$settleTxnId amount:${(channel.myBalance - amount).toPlainString()} address:${channel.myAddress};"
      else "") +
    (if(channel.counterPartyBalance + amount > ZERO)
      "txnoutput id:$settleTxnId amount:${(channel.counterPartyBalance + amount).toPlainString()} address:${channel.counterPartyAddress};"
      else "") +
    "txnsign id:$settleTxnId publickey:${channel.mySettleKey};" +
    "txnexport id:$settleTxnId;"
  val settleTxn = (MDS.cmd(settletxncreator) as Array<dynamic>).last()
  
  publish(
    channelKey(channel.counterPartyTriggerKey, channel.counterPartyUpdateKey, channel.counterPartySettleKey),
    listOf(if(amount > ZERO) "TXN_UPDATE" else "TXN_REQUEST", updateTxn.response.data, settleTxn.response.data).joinToString(";")
  )
}

fun channelKey(vararg keys: String) = keys.joinToString(";")

suspend fun ChannelState.update(isAck: Boolean, updateTx: String, settleTx: String): ChannelState {
  console.log("Updating channel")
  val updateTxnId = newTxId()
  importTx(updateTxnId, updateTx)
  val settleTxnId = newTxId()
  val importedSettleTx = importTx(settleTxnId, settleTx)
  
  if (!isAck) {
    val signedUpdateTx = signAndExportTx(updateTxnId, myUpdateKey)
    val signedSettleTx = signAndExportTx(settleTxnId, mySettleKey)
    publish(channelKey(counterPartyTriggerKey, counterPartyUpdateKey, counterPartySettleKey), listOf("TXN_UPDATE_ACK", signedUpdateTx, signedSettleTx).joinToString(";"))
  }
  val outputs = importedSettleTx["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Output>(it) }
  val channelBalance = outputs.find { it.miniaddress == myAddress }!!.amount to outputs.find { it.miniaddress == counterPartyAddress }!!.amount
  val sequenceNumber = importedSettleTx["state"]!!.jsonArray.map { json.decodeFromJsonElement<State>(it) }.find { it.port == 99 }?.data?.toInt()
  
  return updateChannel(this, channelBalance, sequenceNumber!!, updateTx, settleTx)
}

suspend fun prepareFundChannel(
  myTriggerKey: String,
  myUpdateKey: String,
  mySettleKey: String,
  otherTriggerKey: String,
  otherUpdateKey: String,
  otherSettleKey: String,
  myAddress: String,
  multisigAddress: String,
  eltooAddress: String,
  triggerTx: String,
  settlementTx: String,
  amount: BigDecimal,
  timeLock: Int
): ChannelState {
  MDS.sql("""INSERT INTO channel(
      status, sequence_number, my_balance, other_balance,
      my_trigger_key, my_update_key, my_settle_key,
      other_trigger_key, other_update_key, other_settle_key,
      trigger_tx, update_tx, settle_tx, time_lock,
      multisig_address, eltoo_address, my_address, other_address
    ) VALUES (
      'OFFERED', 0, ${amount.toPlainString()}, 0,
      '$myTriggerKey', '$myUpdateKey', '$mySettleKey',
      '$otherTriggerKey', '$otherUpdateKey', '$otherSettleKey',
      '$triggerTx', '', '$settlementTx', $timeLock,
      '$multisigAddress', '$eltooAddress', '$myAddress', ''
    );
  """)
  val sql = MDS.sql("SELECT IDENTITY() as ID;")
  val results = sql.rows as Array<dynamic>

  publish(
    channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
    listOf(timeLock, myTriggerKey, myUpdateKey, mySettleKey, triggerTx, settlementTx).joinToString(";")
  )
  return ChannelState(
    id = (results[0]["ID"] as String).toInt(),
    sequenceNumber = 0,
    status = "OFFERED",
    myBalance = amount,
    counterPartyBalance = ZERO,
    myAddress = myAddress,
    myTriggerKey = myTriggerKey,
    myUpdateKey = myUpdateKey,
    mySettleKey = mySettleKey,
    counterPartyTriggerKey = otherTriggerKey,
    counterPartyUpdateKey = otherUpdateKey,
    counterPartySettleKey = otherSettleKey,
    triggerTx = triggerTx,
    settlementTx = settlementTx,
    timeLock = timeLock,
    eltooAddress = eltooAddress,
    updatedAt = Date.now().toLong()
  )
}

suspend fun commitFundChannel(
  channel: ChannelState,
  txnId: Int,
  key: String,
  counterPartyAddress: String,
  triggerTx: String,
  settlementTx: String
): ChannelState {
  val txncreator = "txnsign id:$txnId publickey:$key;" +
    "txnpost id:$txnId auto:true;" +
    "txndelete id:$txnId;"
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val status = result.find{it.command == "txnpost"}.status
  console.log("txnpost status", status)
  if (status == true) {
    MDS.sql("""UPDATE channel SET
      other_address = '$counterPartyAddress',
      trigger_tx = '$triggerTx',
      settle_tx = '$settlementTx',
      updated_at = NOW()
      WHERE id = ${channel.id};
    """)
  }
  return channel.copy(triggerTx = triggerTx, settlementTx = settlementTx, counterPartyAddress = counterPartyAddress, updatedAt = Date.now().toLong())
}

suspend fun joinChannel(
  myTriggerKey: String,
  myUpdateKey: String,
  mySettleKey: String,
  otherTriggerKey: String,
  otherUpdateKey: String,
  otherSettleKey: String,
  myAddress: String,
  otherAddress: String,
  multisigAddress: String,
  eltooAddress: String,
  triggerTx: String,
  settlementTx: String,
  amount: BigDecimal,
  timeLock: Int
): ChannelState {
  MDS.sql("""INSERT INTO channel(
      status, sequence_number, my_balance, other_balance,
      my_trigger_key, my_update_key, my_settle_key,
      other_trigger_key, other_update_key, other_settle_key,
      trigger_tx, update_tx, settle_tx, time_lock,
      multisig_address, eltoo_address, my_address, other_address
    ) VALUES (
      'OFFERED', 0, 0, ${amount.toPlainString()},
      '$myTriggerKey', '$myUpdateKey', '$mySettleKey',
      '$otherTriggerKey', '$otherUpdateKey', '$otherSettleKey',
      '$triggerTx', '', '$settlementTx', $timeLock,
      '$multisigAddress', '$eltooAddress', '$myAddress', '$otherAddress'
    );
  """)
  val sql = MDS.sql("SELECT IDENTITY() as ID;")
  val results = sql.rows as Array<dynamic>
  
  publish(
    channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
    listOf(myAddress, triggerTx, settlementTx).joinToString(";")
  )
  return ChannelState(
    id = (results[0]["ID"] as String).toInt(),
    sequenceNumber = 0,
    status = "OFFERED",
    myBalance = ZERO,
    counterPartyBalance = amount,
    myAddress = myAddress,
    counterPartyAddress = otherAddress,
    myTriggerKey = myTriggerKey,
    myUpdateKey = myUpdateKey,
    mySettleKey = mySettleKey,
    counterPartyTriggerKey = otherTriggerKey,
    counterPartyUpdateKey = otherUpdateKey,
    counterPartySettleKey = otherSettleKey,
    triggerTx = triggerTx,
    settlementTx = settlementTx,
    timeLock = timeLock,
    eltooAddress = eltooAddress,
    updatedAt = Date.now().toLong()
  )
}

suspend fun importAndPost(tx: String): dynamic {
  val txId = newTxId()
  importTx(txId, tx)
  return post(txId)
}

suspend fun ChannelState.triggerSettlement(): ChannelState {
  val response = importAndPost(triggerTx)
  return if (response == null) this
    else updateChannelStatus(this, "TRIGGERED")
}

suspend fun ChannelState.postUpdate(): ChannelState {
  val response = importAndPost(updateTx)
  return if (response == null) this
    else updateChannelStatus(this, "UPDATED")
}

suspend fun ChannelState.completeSettlement(): ChannelState {
  val response = importAndPost(settlementTx)
  return if (response == null) this
    else updateChannelStatus(this, "SETTLED")
}
