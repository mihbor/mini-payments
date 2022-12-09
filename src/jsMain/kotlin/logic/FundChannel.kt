package logic

import Channel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.jsonArray
import logic.FundChannelEvent.*
import ltd.mbor.minimak.*
import kotlin.js.Date


enum class FundChannelEvent{
  SCRIPTS_DEPLOYED, FUNDING_TX_CREATED, TRIGGER_TX_SIGNED, SETTLEMENT_TX_SIGNED, CHANNEL_PERSISTED, CHANNEL_PUBLISHED
}

suspend fun prepareFundChannel(
  myKeys: Channel.Keys,
  theirKeys: Channel.Keys,
  amount: BigDecimal,
  tokenId: String,
  timeLock: Int,
  event: (FundChannelEvent) -> Unit = {}
): Pair<Channel, Int> {
  val myAddress = MDS.getAddress()
  multisigScriptAddress = MDS.deployScript(triggerScript(myKeys.trigger, theirKeys.trigger))
  eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, myKeys.update, theirKeys.update, myKeys.settle, theirKeys.settle))
  event(SCRIPTS_DEPLOYED)
  
  val fundingTxId = fundingTx(multisigScriptAddress, amount, tokenId)
  event(FUNDING_TX_CREATED)
  
  val triggerTxId = signFloatingTx(myKeys.trigger, multisigScriptAddress, amount, tokenId, eltooScriptAddress, mapOf(99 to "0"))
  event(TRIGGER_TX_SIGNED)
  
  val settlementTxId = signFloatingTx(myKeys.settle, eltooScriptAddress, amount, tokenId, myAddress, mapOf(99 to "0"))
  event(SETTLEMENT_TX_SIGNED)
  
  val signedTriggerTx = MDS.exportTx(triggerTxId)
  val signedSettlementTx = MDS.exportTx(settlementTxId)
  
  val channelId = insertChannel(tokenId, amount, myKeys, theirKeys, signedTriggerTx, signedSettlementTx, timeLock, multisigScriptAddress, eltooScriptAddress, myAddress, "")
  event(CHANNEL_PERSISTED)
  
  publish(
    theirKeys,
    listOf(timeLock, myKeys.trigger, myKeys.update, myKeys.settle, signedTriggerTx, signedSettlementTx).joinToString(";")
  )
  event(CHANNEL_PUBLISHED)
  
  return Channel(
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
  ) to fundingTxId
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
