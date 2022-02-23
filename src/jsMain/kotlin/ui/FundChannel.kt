package ui

import androidx.compose.runtime.*
import deployScript
import exportTx
import externals.QrScanner
import kotlinx.browser.document
import kotlinx.coroutines.launch
import newKey
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLVideoElement
import scope
import script
import store

@Composable
fun FundChannel() {
  var showFundChannel by remember { mutableStateOf(false) }
  var showFundScanner by remember { mutableStateOf(false) }
  var amount by remember { mutableStateOf(0.0) }
  var tokenId by remember { mutableStateOf("0x00") }
  var timeLock by remember { mutableStateOf(10) }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var otherUpdateKey by remember { mutableStateOf("") }
  var otherSettleKey by remember { mutableStateOf("") }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  
  Button({
    onClick {
      showFundChannel = !showFundChannel
      val canvas = document.getElementById("fundChannelQR") as HTMLCanvasElement
      val video = document.getElementById("fundChannelVideo").also { console.log("video", it) } as HTMLVideoElement
      if (showFundChannel) scope.launch {
        showFundScanner = true
        qrScanner = QrScanner(video) { result ->
          console.log("decoded qr code: $result")
          result.split(';').apply {
            otherUpdateKey = this[0]
            otherSettleKey = this[1]
          }
          qrScanner!!.stop()
          showFundScanner = false
        }.also { it.start() }
        
        myUpdateKey = newKey()
        mySettleKey = newKey()
        QRCode.toCanvas(canvas, "$myUpdateKey;$mySettleKey", { error ->
          if (error != null) console.error(error)
          else console.log("qr generated")
        })
      }
      else {
        console.log("qrScanner", qrScanner)
        qrScanner?.stop()
      }
    }
    style {
      if (showFundChannel) border(style = LineStyle.Inset)
    }
  }) {
    Text("Fund payment channel")
  }
  if (showFundChannel) {
    Br()
    Text("My update key: $myUpdateKey")
    Br()
    Text("My settlement key: $mySettleKey")
    Br()
    Text("Counterparty update key: $otherUpdateKey")
    Br()
    Text("Counterparty settlement key: $otherSettleKey")
    Br()
    if (mySettleKey.isNotEmpty() && myUpdateKey.isNotEmpty() && otherSettleKey.isNotEmpty() && otherUpdateKey.isNotEmpty()) {
      NumberInput(amount, min = 0) {
        onInput {
          amount = it.value!!.toDouble()
        }
      }
      TokenSelect(tokenId) {
        tokenId = it
      }
      Text("Update only time lock (block diff)")
      NumberInput(timeLock, min = 0) {
        onInput {
          timeLock = it.value!!.toInt()
        }
      }
      Button({
        if(amount <= 0) disabled()
        onClick {
          scope.launch {
            val address = deployScript(script(timeLock, myUpdateKey, otherUpdateKey, mySettleKey, otherSettleKey))
            val tx = exportTx(address, amount, tokenId)
            store("$myUpdateKey;$mySettleKey", tx)
          }
        }
      }) {
        Text("Export funding transaction")
      }
    }
  }
  Br()
  Canvas({
    id("fundChannelQR")
    style {
      if (!showFundChannel) display(DisplayStyle.None)
    }
  })
  if(showFundScanner) {
    Br()
    Text("Scan counter party keys QR code")
  }
  Br()
  Video({
    id("fundChannelVideo")
    style {
      if (!showFundScanner) display(DisplayStyle.None)
      width(500.px)
      height(500.px)
    }
  })
}