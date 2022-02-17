package ui

import androidx.compose.runtime.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import newKey
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.NumberInput
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement
import scope

@Composable
fun FundChannel() {
  var showFundChannel by remember { mutableStateOf(false) }
  var amount by remember { mutableStateOf(0.0) }
  var tokenId by remember { mutableStateOf("0x00") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  
  Button({
    onClick {
      showFundChannel = !showFundChannel
      val fundUpdateCanvas = document.getElementById("fundUpdateQR") as HTMLCanvasElement
      val fundSettleCanvas = document.getElementById("fundSettleQR") as HTMLCanvasElement
      if(showFundChannel) scope.launch {
        myUpdateKey = newKey()
        QRCode.toCanvas(fundUpdateCanvas, myUpdateKey, { error ->
          if (error != null) console.error(error)
          else console.log("qr generated")
        })
        mySettleKey = newKey()
        QRCode.toCanvas(fundSettleCanvas, mySettleKey, { error ->
          if (error != null) console.error(error)
          else console.log("qr generated")
        })
      }
    }
    style {
      if (showFundChannel) border(style = LineStyle.Inset)
    }
  }) {
    Text("Fund payment channel")
  }
  if (showFundChannel) {
    NumberInput(amount, min = 0) {
      onInput {
        amount = it.value!!.toDouble()
      }
    }
    TokenSelect(tokenId) {
      tokenId = it
    }
    Button({
//      if(amount <= 0 || toAddress.isEmpty()) disabled()
      onClick {
//        console.log("post $amount [$tokenId] to $toAddress")
        scope.launch {
//          post(toAddress, amount, tokenId)
        }
      }
    }) {
      Text("Export funding transaction")
    }
    Br()
    Text("Update key: $myUpdateKey")
    Br()
    Text("Settlement key: $mySettleKey")
  }
  Br()
  Canvas({
    id("fundUpdateQR")
    style {
      if (!showFundChannel) display(DisplayStyle.None)
    }
  })
  Canvas({
    id("fundSettleQR")
    style {
      if (!showFundChannel) display(DisplayStyle.None)
    }
  })
}