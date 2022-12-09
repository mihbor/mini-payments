package logic

import Channel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import logic.JoinChannelEvent.*
import ltd.mbor.minimak.*
import kotlin.js.Date


enum class JoinChannelEvent{
  SCRIPTS_DEPLOYED, TRIGGER_TX_SIGNED, SETTLEMENT_TX_SIGNED, CHANNEL_PERSISTED, CHANNEL_PUBLISHED
}

suspend fun joinChannel(
  myKeys: Channel.Keys,
  theirKeys: Channel.Keys,
  triggerTx: String,
  settlementTx: String,
  timeLock: Int,
  event: (JoinChannelEvent) -> Unit = {}
): Channel {
  val myAddress = MDS.getAddress()
  multisigScriptAddress = MDS.deployScript(triggerScript(theirKeys.trigger, myKeys.trigger))
  eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, theirKeys.update, myKeys.update, theirKeys.settle, myKeys.settle))
  event(SCRIPTS_DEPLOYED)
  
  val triggerTxId = newTxId()
  val outputs = MDS.importTx(triggerTxId, triggerTx)["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Coin>(it) }
  val (amount, tokenId) = outputs.find { it.address == eltooScriptAddress }!!.let { it.tokenAmount to it.tokenId }
  val signedTriggerTx = signAndExportTx(triggerTxId, myKeys.trigger)
  event(TRIGGER_TX_SIGNED)
  
  val settlementTxId = newTxId()
  val importedSettlementTx = MDS.importTx(settlementTxId, settlementTx)
  val output = json.decodeFromJsonElement<Coin>(importedSettlementTx["outputs"]!!.jsonArray.first())
  val theirAddress = output.miniAddress
  val signedSettlementTx = signAndExportTx(settlementTxId, myKeys.settle)
  event(SETTLEMENT_TX_SIGNED)

  val channelId = insertChannel(tokenId, amount, myKeys, theirKeys, signedTriggerTx, signedSettlementTx, timeLock, multisigScriptAddress, eltooScriptAddress, myAddress, theirAddress)
  event(CHANNEL_PERSISTED)

  publish(
    theirKeys,
    listOf(myAddress, signedTriggerTx, signedSettlementTx).joinToString(";")
  )
  event(CHANNEL_PUBLISHED)

  return Channel(
    id = channelId,
    sequenceNumber = 0,
    status = "OFFERED",
    tokenId = tokenId,
    my = Channel.Side(
      balance = BigDecimal.ZERO,
      address = myAddress,
      keys = myKeys
    ),
    their = Channel.Side(
      balance = amount,
      address = theirAddress,
      keys = theirKeys
    ),
    triggerTx = signedTriggerTx,
    settlementTx = signedSettlementTx,
    timeLock = timeLock,
    eltooAddress = eltooScriptAddress,
    updatedAt = Date.now().toLong()
  )
}
