package ui

import androidx.compose.runtime.*
import externals.QrScanner
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import post
import scope

@Composable
fun Send() {
  
  var showSend by remember { mutableStateOf(false) }
  var toAddress by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf(0.0) }
  var tokenId by remember { mutableStateOf("0x00") }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  
  Button({
    onClick {
      showSend = !showSend
      val video = document.getElementById("video").also { console.log("video", it) } as HTMLVideoElement
      if (showSend) {
        qrScanner = QrScanner(video) { result ->
          console.log("decoded qr code: $result")
          toAddress = result
          qrScanner!!.stop()
        }.also { it.start() }
      } else {
        console.log("qrScanner", qrScanner)
        qrScanner?.stop()
//          qrScanner?.destroy()
//          video.pause()
//          video.removeAttribute("src")
//          video.load()
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
  Br()
  Video({
    id("video")
    style {
      if (!showSend) display(DisplayStyle.None)
      width(500.px)
      height(500.px)
    }
  })
}