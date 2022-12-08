package logic

import ChannelState
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.*
import logic.FundChannelEvent.*
import ltd.mbor.minimak.*
import kotlin.js.Date

suspend fun isPaymentChannelAvailable(toAddress: String, tokenId: String, amount: BigDecimal): Boolean {
  val matchingChannels = getChannels(status = "OPEN").filter {
    it.counterPartyAddress == toAddress && it.tokenId == tokenId && it.myBalance >= amount
  }
  return matchingChannels.isNotEmpty()
}

suspend fun fundingTx(toAddress: String, amount: BigDecimal, tokenId: String): Int {
  val txnId = newTxId()
  val (inputs, outputs) = MDS.inputsWithChange(tokenId, amount)
  
  val txncreator = "txncreate id:$txnId;" +
    inputs.map{ "txninput id:$txnId coinid:${it.coinId};"}.joinToString("") +
    "txnoutput id:$txnId amount:${amount.toPlainString()} address:$toAddress tokenid:$tokenId;" +
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} address:${it.address} tokenid:${it.tokenId};"}.joinToString("")
  
  MDS.cmd(txncreator)
  return txnId
}

suspend fun signAndExportTx(id: Int, key: String): String {
  MDS.signTx(id, key)
  return MDS.exportTx(id)
}

suspend fun importAndPost(tx: String): dynamic {
  val txId = newTxId()
  MDS.importTx(txId, tx)
  return MDS.post(txId)
}

suspend fun signFloatingTx(
  myKey: String,
  sourceScriptAddress: String,
  amount: BigDecimal,
  tokenId: String,
  targetAddress: String,
  states: Map<Int, String> = emptyMap()
): Int {
  
  val txnId = newTxId()
  val txncreator = "txncreate id:$txnId;" +
    "txninput id:$txnId address:${sourceScriptAddress} amount:$amount tokenid:$tokenId floating:true;" +
    states.mapNotNull { (index, value) -> value.takeUnless { it.isEmpty() }?.let {"txnstate id:$txnId port:$index value:$value;" } }.joinToString("") +
    "txnoutput id:$txnId amount:${amount} address:$targetAddress;" +
    "txnsign id:$txnId publickey:$myKey;"
  
  MDS.cmd(txncreator)!!.jsonArray
  return txnId
}

fun channelKey(vararg keys: String) = keys.joinToString(";")

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
  tokenId: String,
  timeLock: Int
): ChannelState {
  MDS.sql("""INSERT INTO channel(
      status, sequence_number, token_id, my_balance, other_balance,
      my_trigger_key, my_update_key, my_settle_key,
      other_trigger_key, other_update_key, other_settle_key,
      trigger_tx, update_tx, settle_tx, time_lock,
      multisig_address, eltoo_address, my_address, other_address
    ) VALUES (
      'OFFERED', 0, '$tokenId', 0, ${amount.toPlainString()},
      '$myTriggerKey', '$myUpdateKey', '$mySettleKey',
      '$otherTriggerKey', '$otherUpdateKey', '$otherSettleKey',
      '$triggerTx', '', '$settlementTx', $timeLock,
      '$multisigAddress', '$eltooAddress', '$myAddress', '$otherAddress'
    );
  """)
  val sql = MDS.sql("SELECT IDENTITY() as ID;")
  val results = sql!!.jsonObject["rows"]!!.jsonArray
  
  publish(
    channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
    listOf(myAddress, triggerTx, settlementTx).joinToString(";")
  )
  return ChannelState(
    id = results[0].jsonString("ID")!!.toInt(),
    sequenceNumber = 0,
    status = "OFFERED",
    tokenId = tokenId,
    myBalance = BigDecimal.ZERO,
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

enum class FundChannelEvent{
  SCRIPTS_DEPLOYED, FUNDING_TX_CREATED, TRIGGER_TX_SIGNED, SETTLEMENT_TX_SIGNED, CHANNEL_PERSISTED, CHANNEL_PUBLISHED
}

suspend fun prepareFundChannel(
  myTriggerKey: String,
  myUpdateKey: String,
  mySettleKey: String,
  otherTriggerKey: String,
  otherUpdateKey: String,
  otherSettleKey: String,
  amount: BigDecimal,
  tokenId: String,
  timeLock: Int,
  event: (FundChannelEvent) -> Unit = {}
): Pair<ChannelState, Int> {
  val myAddress = MDS.getAddress()
  multisigScriptAddress = MDS.deployScript(triggerScript(myTriggerKey, otherTriggerKey))
  console.log("multisig address (fund)", multisigScriptAddress)
  eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, myUpdateKey, otherUpdateKey, mySettleKey, otherSettleKey))
  event(SCRIPTS_DEPLOYED)
  
  val fundingTxId = fundingTx(multisigScriptAddress, amount, tokenId)
  event(FUNDING_TX_CREATED)
  
  val triggerTxId = signFloatingTx(myTriggerKey, multisigScriptAddress, amount, tokenId, eltooScriptAddress, mapOf(99 to "0"))
  event(TRIGGER_TX_SIGNED)
  
  val settlementTxId = signFloatingTx(mySettleKey, eltooScriptAddress, amount, tokenId, myAddress, mapOf(99 to "0"))
  event(SETTLEMENT_TX_SIGNED)
  
  val triggerTx = MDS.exportTx(triggerTxId)
  val settlementTx = MDS.exportTx(settlementTxId)
  MDS.sql("""INSERT INTO channel(
      status, sequence_number, token_id, my_balance, other_balance,
      my_trigger_key, my_update_key, my_settle_key,
      other_trigger_key, other_update_key, other_settle_key,
      trigger_tx, update_tx, settle_tx, time_lock,
      multisig_address, eltoo_address, my_address, other_address
    ) VALUES (
      'OFFERED', 0, '$tokenId', ${amount.toPlainString()}, 0,
      '$myTriggerKey', '$myUpdateKey', '$mySettleKey',
      '$otherTriggerKey', '$otherUpdateKey', '$otherSettleKey',
      '$triggerTx', '', '$settlementTx', $timeLock,
      '$multisigScriptAddress', '$eltooScriptAddress', '$myAddress', ''
    );
  """)
  val sql = MDS.sql("SELECT IDENTITY() as ID;")!!
  val results = sql.jsonObject["rows"]!!.jsonArray
  event(CHANNEL_PERSISTED)
  
  publish(
    channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
    listOf(timeLock, myTriggerKey, myUpdateKey, mySettleKey, triggerTx, settlementTx).joinToString(";")
  )
  event(CHANNEL_PUBLISHED)
  
  return ChannelState(
    id = results[0].jsonString("ID")!!.toInt(),
    sequenceNumber = 0,
    status = "OFFERED",
    tokenId = tokenId,
    myBalance = amount,
    counterPartyBalance = BigDecimal.ZERO,
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
    eltooAddress = eltooScriptAddress,
    updatedAt = Date.now().toLong()
  ) to fundingTxId
}

suspend fun ChannelState.commitFund(
  fundingTxId: Int,
  key: String,
  counterPartyAddress: String,
  triggerTx: String,
  settlementTx: String
): ChannelState {
  MDS.importTx(newTxId(), triggerTx)
  MDS.importTx(newTxId(), settlementTx)
  val txncreator = "txnsign id:$fundingTxId publickey:$key;" +
    "txnpost id:$fundingTxId auto:true;" +
    "txndelete id:$fundingTxId;"
  val result = MDS.cmd(txncreator)!!.jsonArray
  val status = result.find{ it.jsonString("command") == "txnpost" }!!.jsonString("status")
  console.log("txnpost status", status)
  if (status.toBoolean()) {
    MDS.sql("""UPDATE channel SET
      other_address = '$counterPartyAddress',
      trigger_tx = '$triggerTx',
      settle_tx = '$settlementTx',
      updated_at = NOW()
      WHERE id = $id;
    """)
  }
  return copy(triggerTx = triggerTx, settlementTx = settlementTx, counterPartyAddress = counterPartyAddress, updatedAt = Date.now().toLong())
}

suspend fun ChannelState.update(isAck: Boolean, updateTx: String, settleTx: String): ChannelState {
  console.log("Updating channel")
  val updateTxnId = newTxId()
  MDS.importTx(updateTxnId, updateTx)
  val settleTxnId = newTxId()
  val importedSettleTx = MDS.importTx(settleTxnId, settleTx)
  
  if (!isAck) {
    val signedUpdateTx = signAndExportTx(updateTxnId, myUpdateKey)
    val signedSettleTx = signAndExportTx(settleTxnId, mySettleKey)
    publish(channelKey(counterPartyTriggerKey, counterPartyUpdateKey, counterPartySettleKey), listOf("TXN_UPDATE_ACK", signedUpdateTx, signedSettleTx).joinToString(";"))
  }
  val outputs = importedSettleTx["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Coin>(it) }
  val channelBalance = outputs.find { it.miniAddress == myAddress }!!.amount to outputs.find { it.miniAddress == counterPartyAddress }!!.amount
  val sequenceNumber = importedSettleTx["state"]!!.jsonArray.map { json.decodeFromJsonElement<State>(it) }.find { it.port == 99 }?.data?.toInt()
  
  return updateChannel(this, channelBalance, sequenceNumber!!, updateTx, settleTx)
}

suspend fun ChannelState.request(amount: BigDecimal) = this.send(-amount)

suspend fun ChannelState.send(amount: BigDecimal) {
  val currentSettlementTx = MDS.importTx(newTxId(), settlementTx)
  val input = json.decodeFromJsonElement<Coin>(currentSettlementTx["inputs"]!!.jsonArray.first())
  val state = currentSettlementTx["state"]!!.jsonArray.find{ it.jsonObject["port"]!!.jsonPrimitive.int == 99 }!!.jsonObject["data"]!!.jsonPrimitive.content
  val updateTxnId = newTxId()
  val updatetxncreator = "txncreate id:$updateTxnId;" +
    "txninput id:$updateTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenId} floating:true;" +
    "txnstate id:$updateTxnId port:99 value:${state.toInt() + 1};" +
    "txnoutput id:$updateTxnId amount:${input.amount} address:${input.address};" +
    "txnsign id:$updateTxnId publickey:$myUpdateKey;" +
    "txnexport id:$updateTxnId;"
  val updateTxn = MDS.cmd(updatetxncreator)!!.jsonArray.last()
  val settleTxnId = newTxId()
  val settletxncreator = "txncreate id:$settleTxnId;" +
    "txninput id:$settleTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenId} floating:true;" +
    "txnstate id:$settleTxnId port:99 value:${state.toInt() + 1};" +
    (if(myBalance - amount > BigDecimal.ZERO)
      "txnoutput id:$settleTxnId amount:${(myBalance - amount).toPlainString()} address:$myAddress;"
    else "") +
    (if(counterPartyBalance + amount > BigDecimal.ZERO)
      "txnoutput id:$settleTxnId amount:${(counterPartyBalance + amount).toPlainString()} address:$counterPartyAddress;"
    else "") +
    "txnsign id:$settleTxnId publickey:$mySettleKey;" +
    "txnexport id:$settleTxnId;"
  val settleTxn = MDS.cmd(settletxncreator)!!.jsonArray.last()
  
  publish(
    channelKey(counterPartyTriggerKey, counterPartyUpdateKey, counterPartySettleKey),
    listOf(if(amount > BigDecimal.ZERO) "TXN_UPDATE" else "TXN_REQUEST", updateTxn.jsonObject["response"]!!.jsonObject["data"], settleTxn.jsonObject["response"]!!.jsonString("data")).joinToString(";")
  )
}

suspend fun ChannelState.postUpdate(): ChannelState {
  val response = importAndPost(updateTx)
  return if (response == null) this
  else updateChannelStatus(this, "UPDATED")
}

suspend fun ChannelState.triggerSettlement(): ChannelState {
  val response = importAndPost(triggerTx)
  return if (response == null) this
  else updateChannelStatus(this, "TRIGGERED")
}

suspend fun ChannelState.completeSettlement(): ChannelState {
  val response = importAndPost(settlementTx)
  return if (response == null) this
  else updateChannelStatus(this, "SETTLED")
}
