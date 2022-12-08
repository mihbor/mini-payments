package logic

import ChannelState
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.*
import ltd.mbor.minimak.*

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
    "txnoutput id:$txnId amount:${amount.toPlainString()} tokenid:$tokenId address:$toAddress;" +
    outputs.map{ "txnoutput id:$txnId amount:${it.amount.toPlainString()} tokenid:${it.tokenId} address:${it.address};"}.joinToString("")
  
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
    "txnoutput id:$txnId amount:${amount} tokenid:$tokenId address:$targetAddress;" +
    "txnsign id:$txnId publickey:$myKey;"
  
  MDS.cmd(txncreator)!!.jsonArray
  return txnId
}

fun channelKey(vararg keys: String) = keys.joinToString(";")

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
    "txnoutput id:$updateTxnId amount:${input.amount} tokenid:${input.tokenId} address:${input.address};" +
    "txnsign id:$updateTxnId publickey:$myUpdateKey;" +
    "txnexport id:$updateTxnId;"
  val updateTxn = MDS.cmd(updatetxncreator)!!.jsonArray.last()
  val settleTxnId = newTxId()
  val settletxncreator = "txncreate id:$settleTxnId;" +
    "txninput id:$settleTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenId} floating:true;" +
    "txnstate id:$settleTxnId port:99 value:${state.toInt() + 1};" +
    (if(myBalance - amount > BigDecimal.ZERO)
      "txnoutput id:$settleTxnId amount:${(myBalance - amount).toPlainString()} tokenid:${input.tokenId} address:$myAddress;"
    else "") +
    (if(counterPartyBalance + amount > BigDecimal.ZERO)
      "txnoutput id:$settleTxnId amount:${(counterPartyBalance + amount).toPlainString()} tokenid:${input.tokenId} address:$counterPartyAddress;"
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
