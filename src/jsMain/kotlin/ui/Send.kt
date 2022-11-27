package ui

import androidx.compose.runtime.*
import balances
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import externals.QrScanner
import isPaymentChannelAvailable
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import ltd.mbor.minimak.MDS
import ltd.mbor.minimak.send
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import scope

@Composable
fun Send() {
  
  var showSend by remember { mutableStateOf(false) }
  var showCam by remember { mutableStateOf(false) }
  var sending by remember { mutableStateOf(false) }
  var toAddress by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf(ZERO) }
  var tokenId by remember { mutableStateOf("0x00") }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  
  Div({
    classes(StyleSheets.container)
  }) {
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
      Br()
      TextInput(toAddress) {
        onInput {
          toAddress = it.value
        }
        style {
          width(400.px)
        }
      }
      Br()
      DecimalNumberInput(amount, min = ZERO) {
        it?.let { amount = it }
      }
      TokenIcon(tokenId, balances)
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
        if (amount <= 0 || toAddress.isEmpty() || sending) disabled()
        onClick {
          sending = true
          console.log("post $amount [$tokenId] to $toAddress")
          scope.launch {
            if (isPaymentChannelAvailable(toAddress, tokenId, amount) && window.confirm("Found available payment channel. Send in channel instead?")) {
              //TODO: pay in channel instead
            } else {
              MDS.send(toAddress, amount, tokenId)
            }
            showCam = false
            showSend = false
            sending = false
            qrScanner?.stop()
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
}