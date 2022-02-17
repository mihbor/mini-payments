package ui

import androidx.compose.runtime.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import newAddress
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

@Composable
fun Receive() {
  var showReceive by remember { mutableStateOf(false) }
  var myAddress by remember { mutableStateOf("") }
  Button({
    onClick {
      showReceive = !showReceive
      val canvas = document.getElementById("receiveQR") as HTMLCanvasElement
      if(showReceive) scope.launch {
        myAddress = newAddress()
        QRCode.toCanvas(canvas, myAddress, { error ->
          if (error != null) console.error(error)
          else console.log("qr generated")
        })
      } else {
        myAddress = ""
        (canvas.getContext("2d") as CanvasRenderingContext2D).clearRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
      }
    }
    style {
      if (showReceive) border(style = LineStyle.Inset)
    }
  }) {
    Text("Receive")
  }
  Br()
  if (showReceive) Text(myAddress)
  Br()
  Canvas({
    id("receiveQR")
    style {
      if (!showReceive) display(DisplayStyle.None)
    }
  })
}