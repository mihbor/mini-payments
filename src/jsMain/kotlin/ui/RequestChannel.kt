package ui

import ChannelState
import androidx.compose.runtime.*
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import logic.*
import ltd.mbor.minimak.*
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement
import scope

@Composable
fun RequestChannel() {
  var myAddress by remember { mutableStateOf("") }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var counterPartyAddress by remember { mutableStateOf("") }
  var otherTriggerKey by remember { mutableStateOf("") }
  var otherUpdateKey by remember { mutableStateOf("") }
  var otherSettleKey by remember { mutableStateOf("") }
  var timeLock by remember { mutableStateOf(10) }
  
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  
  var channel by remember { mutableStateOf<ChannelState?>(null) }
  
  LaunchedEffect ("requestChannel")  {
    newKeys(3).apply {
      myTriggerKey = this[0]
      myUpdateKey = this[1]
      mySettleKey = this[2]
    }
    triggerTxStatus = ""
    settlementTxStatus = ""
    val canvas = document.getElementById("joinChannelQR") as HTMLCanvasElement
    QRCode.toCanvas(
      canvas, "$myTriggerKey;$myUpdateKey;$mySettleKey"
    ) { error ->
      if (error != null) console.error(error)
      else {
        console.log("qr generated, subscribing to $myTriggerKey;$myUpdateKey;$mySettleKey")
        subscribe(channelKey(myTriggerKey, myUpdateKey, mySettleKey)).onEach { msg ->
          console.log("tx msg", msg)
          val splits = msg.split(";")
          if (splits[0].startsWith("TXN_UPDATE")) {
            channel = channel!!.update(splits[0].endsWith("ACK"), updateTx = splits[1], settleTx = splits[2])
            updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
          } else {
            timeLock = splits[0].toInt()
            otherTriggerKey = splits[1]
            otherUpdateKey = splits[2]
            otherSettleKey = splits[3]
            val triggerTx = splits[4]
            val settlementTx = splits[5]
            multisigScriptAddress = MDS.deployScript(triggerScript(otherTriggerKey, myTriggerKey))
            eltooScriptAddress = MDS.deployScript(eltooScript(timeLock, otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
            newTxId().also { triggerTxId ->
              val outputs = MDS.importTx(triggerTxId, triggerTx)["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Coin>(it) }
              val amount = outputs.find { it.address == eltooScriptAddress }!!.amount
              val tokenId = outputs.find { it.address == eltooScriptAddress }!!.tokenId
              val signedTriggerTx = signAndExportTx(triggerTxId, myTriggerKey)
              triggerTxStatus = "Trigger transaction received, signed"
              newTxId().also { settlementTxId ->
                MDS.importTx(settlementTxId, settlementTx).also {
                  val output = json.decodeFromJsonElement<Coin>(it["outputs"]!!.jsonArray.first())
                  counterPartyAddress = output.miniAddress
                }
                val signedSettlementTx = signAndExportTx(settlementTxId, mySettleKey)
                settlementTxStatus = "Settlement transaction received, signed"
                myAddress = MDS.getAddress()
                channel = joinChannel(
                  myTriggerKey, myUpdateKey, mySettleKey,
                  otherTriggerKey, otherUpdateKey, otherSettleKey,
                  myAddress, counterPartyAddress, multisigScriptAddress, eltooScriptAddress,
                  signedTriggerTx, signedSettlementTx, amount, tokenId, timeLock
                )
                triggerTxStatus += ", sent back"
                settlementTxStatus += ", sent back"
              }
            }
          }
        }.onCompletion {
          console.log("completed")
        }.launchIn(scope)
      }
    };Unit
  }
  Br()
  if (triggerTxStatus.isEmpty()) {
    Text("Trigger key: $myTriggerKey")
    Br()
    Text("Update key: $myUpdateKey")
    Br()
    Text("Settlement key: $mySettleKey")
    Br()
  }
  triggerTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  updateTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  settlementTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  channel?.let {
    ChannelView(it, multisigScriptBalances, eltooScriptCoins[it.eltooAddress] ?: emptyList()) {
      channel = it
    }
  }
  Br()
  if (triggerTxStatus.isEmpty()) {
    Canvas({
      id("joinChannelQR")
    })
    Br()
    Text("Scan QR code on counter party device")
  }
}