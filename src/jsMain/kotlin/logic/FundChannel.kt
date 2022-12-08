package logic

import ChannelState
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import logic.FundChannelEvent.*
import ltd.mbor.minimak.*
import kotlin.js.Date


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
  ltd.mbor.minimak.MDS.importTx(newTxId(), triggerTx)
  ltd.mbor.minimak.MDS.importTx(newTxId(), settlementTx)
  val txncreator = "txnsign id:$fundingTxId publickey:$key;" +
    "txnpost id:$fundingTxId auto:true;" +
    "txndelete id:$fundingTxId;"
  val result = ltd.mbor.minimak.MDS.cmd(txncreator)!!.jsonArray
  val status = result.find{ it.jsonString("command") == "txnpost" }!!.jsonString("status")
  kotlin.js.console.log("txnpost status", status)
  if (status.toBoolean()) {
    ltd.mbor.minimak.MDS.sql("""UPDATE channel SET
      other_address = '$counterPartyAddress',
      trigger_tx = '$triggerTx',
      settle_tx = '$settlementTx',
      updated_at = NOW()
      WHERE id = $id;
    """)
  }
  return copy(triggerTx = triggerTx, settlementTx = settlementTx, counterPartyAddress = counterPartyAddress, updatedAt = kotlin.js.Date.now().toLong())
}
