package ui

import Channel
import androidx.compose.runtime.*
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import logic.*
import logic.JoinChannelEvent.*
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Progress
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement
import scope

@Composable
fun RequestChannel() {
  var myKeys by remember { mutableStateOf(Channel.Keys("", "", "")) }
  var theirKeys by remember { mutableStateOf(Channel.Keys("", "", "")) }
  var timeLock by remember { mutableStateOf(10) }
  
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  
  var progressStep: Int by remember { mutableStateOf(0) }
  
  var channel by remember { mutableStateOf<Channel?>(null) }
  
  LaunchedEffect ("requestChannel")  {
    newKeys(3).apply {
      myKeys = Channel.Keys(this[0], this[1], this[2])
    }
    triggerTxStatus = ""
    settlementTxStatus = ""
    val canvas = document.getElementById("joinChannelQR") as HTMLCanvasElement
    QRCode.toCanvas(
      canvas, channelKey(myKeys)
    ) { error ->
      if (error != null) console.error(error)
      else {
        subscribe(myKeys).onEach { msg ->
          console.log("tx msg", msg)
          val splits = msg.split(";")
          if (splits[0].startsWith("TXN_UPDATE")) {
            channel = channel!!.update(splits[0].endsWith("ACK"), updateTx = splits[1], settleTx = splits[2])
            updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
          } else {
            timeLock = splits[0].toInt()
            theirKeys = Channel.Keys(splits[1], splits[2], splits[3])
            val triggerTx = splits[4]
            val settlementTx = splits[5]
            triggerTxStatus = "Trigger transaction received"
            settlementTxStatus = "Settlement transaction received"
            progressStep++
            
            channel = joinChannel(myKeys, theirKeys, triggerTx, settlementTx, timeLock) {
              progressStep++
              when (it) {
                TRIGGER_TX_SIGNED -> triggerTxStatus += " and signed"
                SETTLEMENT_TX_SIGNED -> settlementTxStatus += " and signed"
                CHANNEL_PUBLISHED -> {
                  triggerTxStatus += " and sent back."
                  settlementTxStatus += " and sent back."
                }
                else -> {}
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
  if (progressStep > 0) {
    Progress({
      attr("value", progressStep.toString())
      attr("max", 6.toString())
      style {
        width(500.px)
      }
    })
    Br()
  }
  if (triggerTxStatus.isEmpty()) {
    Text("Trigger key: ${myKeys.trigger}")
    Br()
    Text("Update key: ${myKeys.update}")
    Br()
    Text("Settlement key: ${myKeys.settle}")
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