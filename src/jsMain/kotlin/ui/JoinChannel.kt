package ui

import androidx.compose.runtime.*
import deployScript
import eltooScript
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
import post
import scope
import store
import subscribe
import triggerScript

@Composable
fun JoinChannel() {
  var showJoinChannel by remember { mutableStateOf(false) }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var triggerTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  var triggerTransactionId by remember { mutableStateOf<Int?>(null) }
  var settlementTransactionId by remember { mutableStateOf<Int?>(null) }
  var timeLock by remember { mutableStateOf(10) }
  
  Button({
    onClick {
      showJoinChannel = !showJoinChannel
      val canvas = document.getElementById("joinChannelQR") as HTMLCanvasElement
      if(showJoinChannel) scope.launch {
        myTriggerKey = newKey()
        myUpdateKey = newKey()
        mySettleKey = newKey()
        triggerTxStatus = ""
        settlementTxStatus= ""
        QRCode.toCanvas(canvas, "$myTriggerKey;$myUpdateKey;$mySettleKey", { error ->
          if (error != null) console.error(error)
          else {
            console.log("qr generated, subscribing to $myTriggerKey;$myUpdateKey;$mySettleKey")
            subscribe("$myTriggerKey;$myUpdateKey;$mySettleKey").onEach { msg ->
              console.log("funding tx msg", msg)
              val splits = msg.split(";")
              timeLock = splits[0].toInt()
              val otherTriggerKey = splits[1]
              val otherUpdateKey = splits[2]
              val otherSettleKey = splits[3]
              val triggerTx = splits[4]
              val settlementTx = splits[5]
              val (signedTriggerTxId, signedTriggerTx) = importSignExportTx(triggerTx, myTriggerKey)
              triggerTransactionId = signedTriggerTxId
              triggerTxStatus = "Trigger transaction receved, signed"
              val (signedSettlementTxId, signedSettlementTx) = importSignExportTx(settlementTx, mySettleKey)
              settlementTransactionId = signedSettlementTxId
              settlementTxStatus = "Settlement transaction receved, signed"
              multisigScriptAddress = deployScript(triggerScript(otherTriggerKey, myTriggerKey))
              eltooScriptAddress = deployScript(eltooScript(timeLock, otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
              store("$otherTriggerKey;$otherUpdateKey;$otherSettleKey", listOf(signedTriggerTx, signedSettlementTx).joinToString(";"))
              triggerTxStatus += ", sent back"
              settlementTxStatus += ", sent back"
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
    Br()
    triggerTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    settlementTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    triggerTransactionId?.let { trigger -> settlementTransactionId?.let{ settle ->
      ChannelFundingView(multisigScriptAddress.isNotEmpty(), timeLock, multisigScriptBalances, eltooScriptCoins,
        {
          post(trigger)
          triggerTxStatus += " and posted!"
        },
        {
          post(settle)
          settlementTxStatus += " and posted!"
        }
      )
    }}
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