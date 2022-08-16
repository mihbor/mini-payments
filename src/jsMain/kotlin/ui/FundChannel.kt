package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import deployScript
import eltooScript
import exportTx
import externals.QrScanner
import fundingTx
import importTx
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import newAddress
import newKey
import newTxId
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import scope
import signAndPost
import signFloatingTx
import store
import subscribe
import triggerScript

@Composable
fun FundChannel() {
  var showFundChannel by remember { mutableStateOf(false) }
  var showFundScanner by remember { mutableStateOf(false) }
  var amount by remember { mutableStateOf(0.0) }
  var tokenId by remember { mutableStateOf("0x00") }
  var timeLock by remember { mutableStateOf(10) }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var otherTriggerKey by remember { mutableStateOf("") }
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
            otherTriggerKey = this[0]
            otherUpdateKey = this[1]
            otherSettleKey = this[2]
          }
          qrScanner!!.stop()
          showFundScanner = false
        }.also { it.start() }
        
        myTriggerKey = newKey()
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
    Text("My trigger key: $myTriggerKey")
    Br()
    Text("My update key: $myUpdateKey")
    Br()
    Text("My settlement key: $mySettleKey")
    Br()
    Text("Counterparty trigger key: $otherTriggerKey")
    Br()
    Text("Counterparty update key: $otherUpdateKey")
    Br()
    Text("Counterparty settlement key: $otherSettleKey")
    Br()
    if (listOf(myTriggerKey, mySettleKey, myUpdateKey, otherTriggerKey, otherSettleKey, otherUpdateKey).all(String::isNotEmpty)) {
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
            val multisigScriptAddress = deployScript(triggerScript(myTriggerKey, otherTriggerKey))
            val eltooScriptAddress = deployScript(eltooScript(timeLock, myUpdateKey, otherUpdateKey, mySettleKey, otherSettleKey))
            console.log("multisig address (fund)", multisigScriptAddress)
            val (fundingTxId, fundingTx) = fundingTx(multisigScriptAddress, amount.toBigDecimal(), tokenId)
            val myAddress = newAddress()
            val (triggerTxId, triggerTx) = signFloatingTx(myTriggerKey, multisigScriptAddress, eltooScriptAddress, fundingTx)
            val (settlementTxId, settlementTx) = signFloatingTx(mySettleKey, eltooScriptAddress, myAddress, triggerTx)
            val exportedTriggerTx = exportTx(triggerTxId)
            val exportedSettlementTx = exportTx(settlementTxId)
            store(
              "$otherTriggerKey;$otherUpdateKey;$otherSettleKey",
              listOf(timeLock, myTriggerKey, myUpdateKey, mySettleKey, exportedTriggerTx, exportedSettlementTx).joinToString(";")
            )
  
            console.log("subscribing to", "$myTriggerKey;$myUpdateKey;$mySettleKey")
            subscribe("$myTriggerKey;$myUpdateKey;$mySettleKey").onEach { msg ->
              console.log("trigger and settlement tx msg", msg)
              val (triggerTx, settlementTx) = msg.split(";")
              importTx(newTxId(), triggerTx)
              importTx(newTxId(), settlementTx)
              signAndPost(fundingTxId, "auto")
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