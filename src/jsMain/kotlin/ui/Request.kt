package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import ltd.mbor.minimak.MDS
import ltd.mbor.minimak.newAddress
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import require
import scope

val QRCode = require("qrcode")

fun drawQR(address: String, tokenId: String, amount:String = "") {
  val canvas = document.getElementById("receiveQR") as HTMLCanvasElement
  QRCode.toCanvas(canvas, "$address;$tokenId;$amount") { error ->
    if (error != null) console.error(error)
    else console.log("qr generated")
  }
}

fun clearQR() {
  val canvas = document.getElementById("receiveQR") as HTMLCanvasElement
  (canvas.getContext("2d") as CanvasRenderingContext2D).clearRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
}

@Composable
fun Receive() {
  var showReceive by remember { mutableStateOf(false) }
  var myAddress by remember { mutableStateOf("") }
  var tokenId by remember { mutableStateOf("0x00") }
  var amount by remember { mutableStateOf(BigDecimal.ZERO) }
  
  Button({
    onClick {
      showReceive = !showReceive
      if (showReceive) scope.launch {
        myAddress = MDS.newAddress()
        drawQR(myAddress, tokenId)
      } else {
        myAddress = ""
        clearQR()
      }
    }
    style {
      if (showReceive) border(style = LineStyle.Inset)
    }
  }) {
    Text("Request")
  }
  Br()
  if (showReceive) {
    Text("My address: $myAddress")
    Br()
    TokenSelect(tokenId) {
      tokenId = it
      drawQR(myAddress, tokenId, amount.toPlainString())
    }
    DecimalNumberInput(amount, min = BigDecimal.ZERO) {
      it?.let {
        amount = it
        drawQR(myAddress, tokenId, amount.toPlainString())
      }
    }
    Button({
      onClick {
        console.log("nfc emit")
        window.open("minipay://localhost:9004/emit?uid=${MDS.minidappuid}&address=$myAddress&token=$tokenId&amount=${amount.toPlainString()}")
      }
    }) {
      Text("Request on NFC (in Android app)")
    }
  }
  Br()
  Canvas({
    id("receiveQR")
    style {
      if (!showReceive) display(DisplayStyle.None)
    }
  })
}