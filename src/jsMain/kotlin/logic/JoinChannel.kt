package logic

import Channel
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import logic.JoinChannelEvent.*
import ltd.mbor.minimak.*
import scope
import kotlin.js.Date


enum class JoinChannelEvent{
  SCRIPTS_DEPLOYED, SIGS_RECEIVED, TRIGGER_TX_SIGNED, SETTLEMENT_TX_SIGNED, CHANNEL_PERSISTED, CHANNEL_PUBLISHED, CHANNEL_UPDATED, CHANNEL_UPDATED_ACKED
}

fun joinChannel(
  myKeys: Channel.Keys,
  event: (JoinChannelEvent, Channel?) -> Unit = { _, _ -> }
) {
  var channel: Channel? = null
  subscribe(myKeys).onEach { msg ->
    console.log("tx msg", msg)
    
    val splits = msg.split(";")
    if (splits[0].startsWith("TXN_UPDATE")) {
      val isAck = splits[0].endsWith("_ACK")
      channel = channel!!.update(isAck, updateTx = splits[1], settleTx = splits[2])
      event(if (isAck) CHANNEL_UPDATED_ACKED else CHANNEL_UPDATED, channel)
    } else {
      val timeLock = splits[0].toInt()
      val theirKeys = Channel.Keys(splits[1], splits[2], splits[3])
      val triggerTx = splits[4]
      val settlementTx = splits[5]
      event(SIGS_RECEIVED, null)
      joinChannel(myKeys, theirKeys, triggerTx, settlementTx, timeLock, event)
    }
  }.onCompletion {
    console.log("completed")
  }.launchIn(scope)
}

suspend fun joinChannel(
  myKeys: Channel.Keys,
  theirKeys: Channel.Keys,
  triggerTx: String,
  settlementTx: String,
  timeLock: Int,
  event: (JoinChannelEvent, Channel?) -> Unit = { _, _ -> }
): Channel {
  val myAddress = MDS.getAddress()
  multisigScriptAddress = MDS.deployScript(triggerScript(theirKeys.trigger, myKeys.trigger))
  eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, theirKeys.update, myKeys.update, theirKeys.settle, myKeys.settle))
  event(SCRIPTS_DEPLOYED, null)
  
  val triggerTxId = newTxId()
  val outputs = MDS.importTx(triggerTxId, triggerTx)["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Coin>(it) }
  val (amount, tokenId) = outputs.find { it.address == eltooScriptAddress }!!.let { it.tokenAmount to it.tokenId }
  val signedTriggerTx = signAndExportTx(triggerTxId, myKeys.trigger)
  event(TRIGGER_TX_SIGNED, null)
  
  val settlementTxId = newTxId()
  val importedSettlementTx = MDS.importTx(settlementTxId, settlementTx)
  val output = json.decodeFromJsonElement<Coin>(importedSettlementTx["outputs"]!!.jsonArray.first())
  val theirAddress = output.miniAddress
  val signedSettlementTx = signAndExportTx(settlementTxId, myKeys.settle)
  event(SETTLEMENT_TX_SIGNED, null)

  val channelId = insertChannel(tokenId, amount, myKeys, theirKeys, signedTriggerTx, signedSettlementTx, timeLock, multisigScriptAddress, eltooScriptAddress, myAddress, theirAddress)
  val channel = Channel(
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
  event(CHANNEL_PERSISTED, channel)

  publish(
    theirKeys,
    listOf(myAddress, signedTriggerTx, signedSettlementTx).joinToString(";")
  )
  event(CHANNEL_PUBLISHED, channel)

  return channel
}
