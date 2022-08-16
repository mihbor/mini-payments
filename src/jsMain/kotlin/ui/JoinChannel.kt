package ui

import androidx.compose.runtime.*
import importSignExportTx
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import newKey
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Video
import org.w3c.dom.HTMLCanvasElement
import scope
import store
import subscribe

@Composable
fun JoinChannel() {
  var showJoinChannel by remember { mutableStateOf(false) }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  
  Button({
    onClick {
      showJoinChannel = !showJoinChannel
      val canvas = document.getElementById("joinChannelQR") as HTMLCanvasElement
      if(showJoinChannel) scope.launch {
        myTriggerKey = newKey()
        myUpdateKey = newKey()
        mySettleKey = newKey()
        QRCode.toCanvas(canvas, "$myTriggerKey;$myUpdateKey;$mySettleKey", { error ->
          if (error != null) console.error(error)
          else {
            console.log("qr generated, subscribing to $myTriggerKey;$myUpdateKey;$mySettleKey")
            subscribe("$myTriggerKey;$myUpdateKey;$mySettleKey").onEach { msg ->
              console.log("funding tx msg", msg)
              val splits = msg.split(";")
              val timeLock = splits[0].toInt()
              val otherTriggerKey = splits[1]
              val otherUpdateKey = splits[2]
              val otherSettleKey = splits[3]
              val triggerTx = splits[4]
              val settlementTx = splits[5]
              val signedTriggerTx = importSignExportTx(triggerTx, myTriggerKey)
              val signedSettlementTx = importSignExportTx(settlementTx, mySettleKey)
//              val multisigScriptAddress = deployScript(triggerScript(otherTriggerKey, myTriggerKey))
//              val eltooScriptAddress = deployScript(eltooScript(timeLock, otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
              store("$otherTriggerKey;$otherUpdateKey;$otherSettleKey", listOf(signedTriggerTx, signedSettlementTx).joinToString(";"))
            }.onCompletion {
              console.log("completed")
            }.launchIn(scope)
          }
        })
      }
    }
    style {
      if (showJoinChannel) border(style = LineStyle.Inset)
    }
  }) {
    Text("Join payment channel")
  }
  if (showJoinChannel) {
    Br()
    Text("Trigger key: $myTriggerKey")
    Br()
    Text("Update key: $myUpdateKey")
    Br()
    Text("Settlement key: $mySettleKey")
  }
  Br()
  Canvas({
    id("joinChannelQR")
    style {
      if (!showJoinChannel) display(DisplayStyle.None)
    }
  })
  if(showJoinChannel) {
    Br()
    Text("Scan QR code on counter party device")
  }
  Br()
  Video({
    id("joinChannelVideo")
    style {
      if (!showJoinChannel) display(DisplayStyle.None)
      width(500.px)
      height(500.px)
    }
  })
}