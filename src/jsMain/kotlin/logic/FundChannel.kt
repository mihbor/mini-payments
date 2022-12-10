package logic

import Channel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.jsonArray
import logic.FundChannelEvent.*
import ltd.mbor.minimak.*
import scope
import kotlin.js.Date

enum class FundChannelEvent{
  SCRIPTS_DEPLOYED, FUNDING_TX_CREATED, TRIGGER_TX_SIGNED, SETTLEMENT_TX_SIGNED, CHANNEL_PERSISTED, CHANNEL_PUBLISHED, SIGS_RECEIVED, CHANNEL_FUNDED, CHANNEL_UPDATED, CHANNEL_UPDATED_ACKED
}

suspend fun fundChannel(
  myKeys: Channel.Keys,
  theirKeys: Channel.Keys,
  amount: BigDecimal,
  tokenId: String,
  timeLock: Int,
  event: (FundChannelEvent, Channel?) -> Unit = { _, _ -> }
) {
  var (channel, fundingTxId) = prepareFundChannel(myKeys, theirKeys, amount, tokenId, timeLock, event)
  
  subscribe(myKeys).onEach { msg ->
    console.log("tx msg", msg)
    val splits = msg.split(";")
    if (splits[0].startsWith("TXN_UPDATE")) {
      val isAck = splits[0].endsWith("_ACK")
      channel = channel.update(isAck, updateTx = splits[1], settleTx = splits[2])
      event(if (isAck) CHANNEL_UPDATED_ACKED else CHANNEL_UPDATED, channel)
    } else {
      val (theirAddress, triggerTx, settlementTx) = splits
      event(SIGS_RECEIVED, channel)
      channel = channel.commitFund(fundingTxId, "auto", theirAddress, triggerTx, settlementTx)
      event(CHANNEL_FUNDED, channel)
    }
  }.onCompletion {
    console.log("completed")
  }.launchIn(scope)
}

suspend fun prepareFundChannel(
  myKeys: Channel.Keys,
  theirKeys: Channel.Keys,
  amount: BigDecimal,
  tokenId: String,
  timeLock: Int,
  event: (FundChannelEvent, Channel?) -> Unit = { _, _ -> }
): Pair<Channel, Int> {
  val myAddress = MDS.getAddress()
  multisigScriptAddress = MDS.deployScript(triggerScript(myKeys.trigger, theirKeys.trigger))
  eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, myKeys.update, theirKeys.update, myKeys.settle, theirKeys.settle))
  event(SCRIPTS_DEPLOYED, null)
  
  val fundingTxId = fundingTx(multisigScriptAddress, amount, tokenId)
  event(FUNDING_TX_CREATED, null)
  
  val triggerTxId = signFloatingTx(myKeys.trigger, multisigScriptAddress, amount, tokenId, eltooScriptAddress, mapOf(99 to "0"))
  event(TRIGGER_TX_SIGNED, null)
  
  val settlementTxId = signFloatingTx(myKeys.settle, eltooScriptAddress, amount, tokenId, myAddress, mapOf(99 to "0"))
  event(SETTLEMENT_TX_SIGNED, null)
  
  val signedTriggerTx = MDS.exportTx(triggerTxId)
  val signedSettlementTx = MDS.exportTx(settlementTxId)
  
  val channelId = insertChannel(tokenId, amount, myKeys, theirKeys, signedTriggerTx, signedSettlementTx, timeLock, multisigScriptAddress, eltooScriptAddress, myAddress, "")
  val channel = Channel(
    id = channelId,
    sequenceNumber = 0,
    status = "OFFERED",
    tokenId = tokenId,
    my = Channel.Side(
      balance = amount,
      address = myAddress,
      keys = myKeys
    ),
    their = Channel.Side(
      balance = BigDecimal.ZERO,
      address = "",
      keys = theirKeys
    ),
    triggerTx = signedTriggerTx,
    settlementTx = signedSettlementTx,
    timeLock = timeLock,
    eltooAddress = eltooScriptAddress,
    updatedAt = Date.now().toLong()
  )
  event(CHANNEL_PERSISTED, channel)
  
  publish(
    theirKeys,
    listOf(timeLock, myKeys.trigger, myKeys.update, myKeys.settle, signedTriggerTx, signedSettlementTx).joinToString(";")
  )
  event(CHANNEL_PUBLISHED, channel)
  
  return channel to fundingTxId
}

suspend fun Channel.commitFund(
  fundingTxId: Int,
  key: String,
  counterPartyAddress: String,
  triggerTx: String,
  settlementTx: String
): Channel {
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
  return copy(triggerTx = triggerTx, settlementTx = settlementTx, their = their.copy(address = counterPartyAddress), updatedAt = Date.now().toLong())
}
