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
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement
import scope

@Composable
fun JoinChannel() {
  var showJoinChannel by remember { mutableStateOf(false) }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  
  Button({
    onClick {
      showJoinChannel = !showJoinChannel
      val joinUpdateCanvas = document.getElementById("joinUpdateQR") as HTMLCanvasElement
      val joinSettleCanvas = document.getElementById("joinSettleQR") as HTMLCanvasElement
      if(showJoinChannel) scope.launch {
        myUpdateKey = newKey()
        QRCode.toCanvas(joinUpdateCanvas, myUpdateKey, { error ->
          if (error != null) console.error(error)
          else console.log("qr generated")
        })
        mySettleKey = newKey()
        QRCode.toCanvas(joinSettleCanvas, mySettleKey, { error ->
          if (error != null) console.error(error)
          else console.log("qr generated")
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
    id("joinUpdateQR")
    style {
      if (!showJoinChannel) display(DisplayStyle.None)
    }
  })
  Canvas({
    id("joinSettleQR")
    style {
      if (!showJoinChannel) display(DisplayStyle.None)
    }
  })
}