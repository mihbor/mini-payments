package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import deployScript
import exportTx
import externals.QrScanner
import importTx
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import multisigScript
import newAddress
import newKey
import newTxId
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import scope
import signAndPost
import store
import subscribe

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
            val multisigScriptAddress = deployScript(multisigScript(timeLock, myUpdateKey, otherUpdateKey, mySettleKey, otherSettleKey))
            console.log("multisig address (fund)", multisigScriptAddress)
            val fundingTx = exportTx(multisigScriptAddress, amount.toBigDecimal(), tokenId)
            store("$otherUpdateKey;$otherSettleKey", listOf(timeLock, myUpdateKey, mySettleKey, newAddress(), fundingTx).joinToString(";"))
  
            console.log("subscribing to", "$myUpdateKey;$mySettleKey")
            subscribe("$myUpdateKey;$mySettleKey").onEach { msg ->
              console.log("settlement tx msg", msg)
              val id = newTxId()
              importTx(id, msg)
              signAndPost(id, mySettleKey)
            }.onCompletion {
              console.log("completed")
            }.launchIn(scope)
          }
        }
      }) {
        Text("Export funding transaction")
      }
    }
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