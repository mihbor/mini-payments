import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import minima.Minima
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable

val scope = MainScope()
external fun require(module: String): dynamic

fun main() {
  Minima.debug = true
  Minima.init{}
  
  val QRCode = require("qrcode")
  
  renderComposable("root") {
    var address by remember { mutableStateOf("") }
    Button({
      onClick {
        scope.launch {
          val newaddress = Minima.cmd("newaddress")
          address = newaddress.response.address.miniaddress
          val canvas = document.getElementById("canvas")
          QRCode.toCanvas(canvas, address, { error ->
            if (error != null) console.error(error)
            else console.log("success!")
          })
        }
      }
    }) {
      Text("New Address")
    }
    Br {  }
    Text(address)
  }
}