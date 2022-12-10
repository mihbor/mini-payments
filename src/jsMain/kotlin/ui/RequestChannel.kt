package ui

import Channel
import androidx.compose.runtime.*
import kotlinx.browser.document
import logic.*
import logic.JoinChannelEvent.*
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Progress
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement

@Composable
fun RequestChannel() {
  var myKeys by remember { mutableStateOf(Channel.Keys("", "", "")) }
  
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
        joinChannel(myKeys) { event, newChannel ->
          progressStep++
          when (event) {
            SIGS_RECEIVED -> {
              triggerTxStatus = "Trigger transaction received"
              settlementTxStatus = "Settlement transaction received"
            }
            TRIGGER_TX_SIGNED -> triggerTxStatus += " and signed"
            SETTLEMENT_TX_SIGNED -> settlementTxStatus += " and signed"
            CHANNEL_PUBLISHED -> {
              channel = newChannel
              triggerTxStatus += " and sent back."
              settlementTxStatus += " and sent back."
            }
            CHANNEL_UPDATED -> {
              channel = newChannel
              updateTxStatus += "Update transaction received. "
            }
            CHANNEL_UPDATED_ACKED -> {
              channel = newChannel
              updateTxStatus += "Update transaction ack received. "
            }
            else -> {}
          }
        }
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