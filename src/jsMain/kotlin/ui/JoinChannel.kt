package ui

import androidx.compose.runtime.*
import deployScript
import exportSettlement
import importTx
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import multisigScript
import newKey
import newTxId
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
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  
  Button({
    onClick {
      showJoinChannel = !showJoinChannel
      val canvas = document.getElementById("joinChannelQR") as HTMLCanvasElement
      if(showJoinChannel) scope.launch {
        myUpdateKey = newKey()
        mySettleKey = newKey()
        QRCode.toCanvas(canvas, "$myUpdateKey;$mySettleKey", { error ->
          if (error != null) console.error(error)
          else {
            console.log("qr generated")
            subscribe("$myUpdateKey;$mySettleKey").onEach { msg ->
              console.log("funding tx msg", msg)
              val (timeLock, otherUpdateKey, otherSettleKey, otherAddress, fundingTx) = msg.split(";")
              val importedTx = importTx(newTxId(), fundingTx)
              val multisigScriptAddress = deployScript(multisigScript(timeLock.toInt(), otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
              console.log("multisig address (join)", multisigScriptAddress)
              val initialSettlementTx = exportSettlement(mySettleKey, multisigScriptAddress, otherAddress, importedTx)
              console.log("publishing to", "$otherUpdateKey;$otherSettleKey", "tx size", initialSettlementTx.length)
              store("$otherUpdateKey;$otherSettleKey", initialSettlementTx)
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