package logic

import ChannelState
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import logic.JoinChannelEvent.*
import ltd.mbor.minimak.*
import kotlin.js.Date


enum class JoinChannelEvent{
  SCRIPTS_DEPLOYED, TRIGGER_TX_SIGNED, SETTLEMENT_TX_SIGNED, CHANNEL_PERSISTED, CHANNEL_PUBLISHED
}

suspend fun joinChannel(
  myTriggerKey: String,
  myUpdateKey: String,
  mySettleKey: String,
  otherTriggerKey: String,
  otherUpdateKey: String,
  otherSettleKey: String,
  triggerTx: String,
  settlementTx: String,
  timeLock: Int,
  event: (JoinChannelEvent) -> Unit = {}
): ChannelState {
  val myAddress = MDS.getAddress()
  multisigScriptAddress = MDS.deployScript(triggerScript(otherTriggerKey, myTriggerKey))
  eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
  event(SCRIPTS_DEPLOYED)
  
  val triggerTxId = newTxId()
  val outputs = MDS.importTx(triggerTxId, triggerTx)["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Coin>(it) }
  val (amount, tokenId) = outputs.find { it.address == eltooScriptAddress }!!.let { it.tokenAmount to it.tokenId }
  val signedTriggerTx = signAndExportTx(triggerTxId, myTriggerKey)
  event(TRIGGER_TX_SIGNED)
  
  val settlementTxId = newTxId()
  val importedSettlementTx = MDS.importTx(settlementTxId, settlementTx)
  val output = json.decodeFromJsonElement<Coin>(importedSettlementTx["outputs"]!!.jsonArray.first())
  val otherAddress = output.miniAddress
  val signedSettlementTx = signAndExportTx(settlementTxId, mySettleKey)
  event(SETTLEMENT_TX_SIGNED)
  
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
      '$signedTriggerTx', '', '$signedSettlementTx', $timeLock,
      '$multisigScriptAddress', '$eltooScriptAddress', '$myAddress', '$otherAddress'
    );
  """)
  val sql = MDS.sql("SELECT IDENTITY() as ID;")
  val results = sql!!.jsonObject["rows"]!!.jsonArray
  event(CHANNEL_PERSISTED)

  publish(
    channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
    listOf(myAddress, signedTriggerTx, signedSettlementTx).joinToString(";")
  )
  event(CHANNEL_PUBLISHED)

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
    triggerTx = signedTriggerTx,
    settlementTx = signedSettlementTx,
    timeLock = timeLock,
    eltooAddress = eltooScriptAddress,
    updatedAt = Date.now().toLong()
  )
}
