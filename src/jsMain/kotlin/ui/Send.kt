package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import externals.QrScanner
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import minima.MDS
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import scope
import send

@Composable
fun Send() {
  
  var showSend by remember { mutableStateOf(false) }
  var showCam by remember { mutableStateOf(false) }
  var toAddress by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf(ZERO) }
  var tokenId by remember { mutableStateOf("0x00") }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  
  Button({
    onClick {
      showSend = !showSend
      showCam = showSend
      val video = document.getElementById("sendVideo").also { console.log("video", it) } as HTMLVideoElement
      if (showSend) {
        qrScanner = QrScanner(video) { result ->
          console.log("decoded qr code: $result")
          val splits = result.split(";")
          toAddress = splits[0]
          if (splits.size > 1 && splits[1].isNotEmpty()) tokenId = splits[1]
          if (splits.size > 2 && splits[2].isNotEmpty()) splits[2].toBigDecimalOrNull()?.let { amount = it }
          qrScanner!!.stop()
          showCam = false
        }.also { it.start() }
      } else {
        console.log("qrScanner", qrScanner)
        qrScanner?.stop()
      }
    }
    style {
      if (showSend) border(style = LineStyle.Inset)
    }
  }) {
    Text("Send")
  }
  if (showSend) {
    Br { }
    TextInput(toAddress) {
      onInput {
        toAddress = it.value
      }
      style {
        width(400.px)
      }
    }
    DecimalNumberInput(amount, min = ZERO) {
      it?.let { amount = it }
    }
    TokenSelect(tokenId) {
      tokenId = it
    }
    Button({
      onClick {
        console.log("nfc read")
        window.open("minipay://localhost:9004/read?uid=${MDS.minidappuid}")
      }
    }) {
      Text("Read NFC (in Android app)")
    }
    Button({
      if(amount <= 0 || toAddress.isEmpty()) disabled()
      onClick {
        console.log("post $amount [$tokenId] to $toAddress")
        showSend = false
        qrScanner?.stop()
        scope.launch {
          send(toAddress, amount, tokenId)
        }
      }
    }) {
      Text("Send!")
    }
  }
  Br()
  Video({
    id("sendVideo")
    style {
      if (!showCam) display(DisplayStyle.None)
      width(500.px)
      height(500.px)
    }
  })
}