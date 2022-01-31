import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ionspin.kotlin.bignum.serialization.kotlinx.bigdecimal.bigDecimalHumanReadableSerializerModule
import externals.QrScanner
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLVideoElement

val scope = MainScope()
val json = Json {
  serializersModule = bigDecimalHumanReadableSerializerModule
}
external fun require(module: String): dynamic

fun main() {
  init()
  
  val QRCode = require("qrcode")
  
  renderComposable("root") {
    var showReceive by remember { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var myAddress by remember { mutableStateOf("") }
    var toAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(0.0) }
    var tokenId by remember { mutableStateOf("0x00") }
    var qrScanner: QrScanner? = null
    Button({
      onClick {
        showReceive = !showReceive
        val canvas = document.getElementById("canvas") as HTMLCanvasElement
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
    Br{ }
    if (showReceive) Text(myAddress)
    Br{ }
    Button({
      onClick {
        showSend = !showSend
        val video = document.getElementById("video") as HTMLVideoElement
        if (showSend) {
          qrScanner = QrScanner(video) { result ->
            console.log("decoded qr code: $result")
            toAddress = result
            qrScanner!!.stop()
          }.also { it.start() }
        } else {
          qrScanner?.stop()
          qrScanner?.destroy()
          video.pause()
          video.removeAttribute("src")
          video.load()
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
      NumberInput(amount, min = 0) {
        onInput {
          amount = it.value!!.toDouble()
        }
      }
      TokenSelect(tokenId) {
        tokenId = it
      }
      Button({
        if(amount <= 0 || toAddress.isEmpty()) disabled()
        onClick {
          console.log("post $amount [$tokenId] to $toAddress")
          showSend = false
          scope.launch {
            post(toAddress, amount, tokenId)
          }
        }
      }) {
        Text("Post")
      }
    }
  }
}