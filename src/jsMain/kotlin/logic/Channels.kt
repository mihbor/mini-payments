package logic

import Channel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.*
import ltd.mbor.minimak.*

fun channelKey(keys: Channel.Keys) = listOf(keys.trigger, keys.update, keys.settle).joinToString(";")

suspend fun isPaymentChannelAvailable(toAddress: String, tokenId: String, amount: BigDecimal): Boolean {
  val matchingChannels = getChannels(status = "OPEN").filter { channel ->
    channel.their.address == toAddress && channel.tokenId == tokenId && channel.my.balance >= amount
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

suspend fun Channel.update(isAck: Boolean, updateTx: String, settleTx: String): Channel {
  console.log("Updating channel")
  val updateTxnId = newTxId()
  MDS.importTx(updateTxnId, updateTx)
  val settleTxnId = newTxId()
  val importedSettleTx = MDS.importTx(settleTxnId, settleTx)
  
  if (!isAck) {
    val signedUpdateTx = signAndExportTx(updateTxnId, my.keys.update)
    val signedSettleTx = signAndExportTx(settleTxnId, my.keys.settle)
    publish(their.keys, listOf("TXN_UPDATE_ACK", signedUpdateTx, signedSettleTx).joinToString(";"))
  }
  val outputs = importedSettleTx["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Coin>(it) }
  val channelBalance = outputs.find { it.miniAddress == my.address }!!.tokenAmount to outputs.find { it.miniAddress == their.address }!!.tokenAmount
  val sequenceNumber = importedSettleTx["state"]!!.jsonArray.map { json.decodeFromJsonElement<State>(it) }.find { it.port == 99 }?.data?.toInt()
  
  return updateChannel(this, channelBalance, sequenceNumber!!, updateTx, settleTx)
}

suspend fun Channel.request(amount: BigDecimal) = this.send(-amount)

suspend fun Channel.send(amount: BigDecimal) {
  val currentSettlementTx = MDS.importTx(newTxId(), settlementTx)
  val input = json.decodeFromJsonElement<Coin>(currentSettlementTx["inputs"]!!.jsonArray.first())
  val state = currentSettlementTx["state"]!!.jsonArray.find{ it.jsonObject["port"]!!.jsonPrimitive.int == 99 }!!.jsonObject["data"]!!.jsonPrimitive.content
  val updateTxnId = newTxId()
  val updatetxncreator = "txncreate id:$updateTxnId;" +
    "txninput id:$updateTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenId} floating:true;" +
    "txnstate id:$updateTxnId port:99 value:${state.toInt() + 1};" +
    "txnoutput id:$updateTxnId amount:${input.amount} tokenid:${input.tokenId} address:${input.address};" +
    "txnsign id:$updateTxnId publickey:${my.keys.update};" +
    "txnexport id:$updateTxnId;"
  val updateTxn = MDS.cmd(updatetxncreator)!!.jsonArray.last()
  val settleTxnId = newTxId()
  val settletxncreator = "txncreate id:$settleTxnId;" +
    "txninput id:$settleTxnId address:${input.address} amount:${input.amount} tokenid:${input.tokenId} floating:true;" +
    "txnstate id:$settleTxnId port:99 value:${state.toInt() + 1};" +
    (if(my.balance - amount > BigDecimal.ZERO)
      "txnoutput id:$settleTxnId amount:${(my.balance - amount).toPlainString()} tokenid:${input.tokenId} address:${my.address};"
    else "") +
    (if(their.balance + amount > BigDecimal.ZERO)
      "txnoutput id:$settleTxnId amount:${(their.balance + amount).toPlainString()} tokenid:${input.tokenId} address:${their.address};"
    else "") +
    "txnsign id:$settleTxnId publickey:${my.keys.settle};" +
    "txnexport id:$settleTxnId;"
  val settleTxn = MDS.cmd(settletxncreator)!!.jsonArray.last()
  
  publish(
    their.keys,
    listOf(if(amount > BigDecimal.ZERO) "TXN_UPDATE" else "TXN_REQUEST", updateTxn.jsonObject["response"]!!.jsonObject["data"], settleTxn.jsonObject["response"]!!.jsonString("data")).joinToString(";")
  )
}

suspend fun Channel.postUpdate(): Channel {
  val response = importAndPost(updateTx)
  return if (response == null) this
  else updateChannelStatus(this, "UPDATED")
}

suspend fun Channel.triggerSettlement(): Channel {
  val response = importAndPost(triggerTx)
  return if (response == null) this
  else updateChannelStatus(this, "TRIGGERED")
}

suspend fun Channel.completeSettlement(): Channel {
  val response = importAndPost(settlementTx)
  return if (response == null) this
  else updateChannelStatus(this, "SETTLED")
}
