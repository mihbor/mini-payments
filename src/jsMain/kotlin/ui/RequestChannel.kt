package ui

import ChannelState
import androidx.compose.runtime.*
import eltooScript
import eltooScriptAddress
import eltooScriptCoins
import joinChannel
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import minima.*
import multisigScriptAddress
import multisigScriptBalances
import newTxId
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement
import scope
import signAndExportTx
import subscribe
import triggerScript
import update

@Composable
fun RequestChannel() {
  var showJoinChannel by remember { mutableStateOf(false) }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var otherTriggerKey by remember { mutableStateOf("") }
  var otherUpdateKey by remember { mutableStateOf("") }
  var otherSettleKey by remember { mutableStateOf("") }
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  var timeLock by remember { mutableStateOf(10) }
  var myAddress by remember { mutableStateOf("") }
  var counterPartyAddress by remember { mutableStateOf("") }
  var channel by remember { mutableStateOf<ChannelState?>(null) }
  
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
        QRCode.toCanvas(canvas, "$myTriggerKey;$myUpdateKey;$mySettleKey"
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
                multisigScriptAddress = deployScript(triggerScript(otherTriggerKey, myTriggerKey))
                eltooScriptAddress = deployScript(eltooScript(timeLock, otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
                newTxId().also { triggerTxId ->
                  val outputs = importTx(triggerTxId, triggerTx)["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Output>(it) }
                  val amount = outputs.find { it.address == eltooScriptAddress }!!.amount
                  val signedTriggerTx = signAndExportTx(triggerTxId, myTriggerKey)
                  triggerTxStatus = "Trigger transaction receved, signed"
                  newTxId().also { settlementTxId ->
                    importTx(settlementTxId, settlementTx).also {
                      val output = json.decodeFromJsonElement<Output>(it["outputs"]!!.jsonArray.first())
                      counterPartyAddress = output.miniaddress
                    }
                    val signedSettlementTx = signAndExportTx(settlementTxId, mySettleKey)
                    settlementTxStatus = "Settlement transaction receved, signed"
                    myAddress = getAddress()
                    channel = joinChannel(
                      myTriggerKey, myUpdateKey, mySettleKey,
                      otherTriggerKey, otherUpdateKey, otherSettleKey,
                      myAddress, counterPartyAddress, multisigScriptAddress, eltooScriptAddress,
                      signedTriggerTx, signedSettlementTx, amount, timeLock
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
    }
    style {
      if (showJoinChannel) border(style = LineStyle.Inset)
    }
  }) {
    Text("Request payment channel")
  }
  if (showJoinChannel) {
    Br()
    if(triggerTxStatus.isEmpty()) {
      Text("Trigger key: $myTriggerKey")
      Br()
      Text("Update key: $myUpdateKey")
      Br()
      Text("Settlement key: $mySettleKey")
      Br()
    }
    triggerTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    updateTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    settlementTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    channel?.let {
      ChannelView(it, multisigScriptBalances, eltooScriptCoins[it.eltooAddress] ?: emptyList())
    }
  }
  Br()
  Canvas({
    id("joinChannelQR")
    style {
      if (!showJoinChannel || triggerTxStatus.isNotEmpty()) display(DisplayStyle.None)
    }
  })
  if(showJoinChannel && triggerTxStatus.isEmpty()) {
    Br()
    Text("Scan QR code on counter party device")
  }
}