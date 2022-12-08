package ui

import ChannelState
import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import externals.QrScanner
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logic.*
import logic.FundChannelEvent.*
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import scope

@Composable
fun FundChannel() {
  var amount by remember { mutableStateOf(ZERO) }
  var tokenId by remember { mutableStateOf("0x00") }
  
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var counterPartyAddress by remember { mutableStateOf("") }
  var otherTriggerKey by remember { mutableStateOf("") }
  var otherUpdateKey by remember { mutableStateOf("") }
  var otherSettleKey by remember { mutableStateOf("") }
  var timeLock by remember { mutableStateOf(10) }
  
  var fundingTxStatus by remember { mutableStateOf("") }
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  
  var showFundScanner by remember { mutableStateOf(false) }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  var progressStep: Int by remember { mutableStateOf(0) }
  
  var channel by remember { mutableStateOf<ChannelState?>(null) }
  
  LaunchedEffect("fundChannel") {
    newKeys(3).apply {
      myTriggerKey = this[0]
      myUpdateKey = this[1]
      mySettleKey = this[2]
    }
    fundingTxStatus = ""
    triggerTxStatus = ""
    settlementTxStatus = ""
  }
  Br()
  if (progressStep > 0) {
    Progress({
      attr("value", progressStep.toString())
      attr("max", 8.toString())
      style {
        width(500.px)
      }
    })
    Br()
  }
  if (fundingTxStatus.isEmpty()) {
    Text("My trigger key: $myTriggerKey")
    Br()
    Text("My update key: $myUpdateKey")
    Br()
    Text("My settlement key: $mySettleKey")
    Br()
    Text("Counterparty trigger key:")
    TextInput(otherTriggerKey) {
      onInput {
        otherTriggerKey = it.value
      }
      style {
        width(500.px)
      }
    }
    Br()
    Text("Counterparty update key:")
    TextInput(otherUpdateKey) {
      onInput {
        otherUpdateKey = it.value
      }
      style {
        width(500.px)
      }
    }
    Br()
    Text("Counterparty settlement key:")
    TextInput(otherSettleKey) {
      onInput {
        otherSettleKey = it.value
      }
      style {
        width(500.px)
      }
    }
    Br()
  }
  fundingTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  triggerTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  updateTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  settlementTxStatus.takeUnless { it.isEmpty() }?.let {
    Text(it)
    Br()
  }
  channel?.let {
    ChannelView(it, multisigScriptBalances, eltooScriptCoins[it.eltooAddress] ?: emptyList()) {
      channel = it
    }
  }
  if (listOf(myTriggerKey, mySettleKey, myUpdateKey, otherTriggerKey, otherSettleKey, otherUpdateKey).all(String::isNotEmpty)
    && fundingTxStatus.isEmpty()
  ) {
    DecimalNumberInput(amount, min = ZERO) {
      it?.let { amount = it }
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
      if (amount <= 0) disabled()
      onClick {
        showFundScanner = false
        qrScanner?.stop()
        scope.launch {
          val (channelNotPosted, fundingTxId) = prepareFundChannel(
            myTriggerKey, myUpdateKey, mySettleKey,
            otherTriggerKey, otherUpdateKey, otherSettleKey,
            amount, tokenId, timeLock
          ) {
            progressStep++
            when(it) {
              FUNDING_TX_CREATED -> fundingTxStatus = "Funding transaction created"
              TRIGGER_TX_SIGNED -> triggerTxStatus = "Trigger transaction created and signed"
              SETTLEMENT_TX_SIGNED -> settlementTxStatus = "Settlement transaction created and signed"
              CHANNEL_PUBLISHED -> {
                triggerTxStatus += ", sent"
                settlementTxStatus += ", sent"
              }
              else -> {}
            }
          }
          channel = channelNotPosted
          console.log("channelId", channel!!.id)
        
          console.log("subscribing to", "$myTriggerKey;$myUpdateKey;$mySettleKey")
          subscribe(channelKey(myTriggerKey, myUpdateKey, mySettleKey)).onEach { msg ->
            console.log("tx msg", msg)
            val splits = msg.split(";")
            if (splits[0].startsWith("TXN_UPDATE")) {
              channel = channel!!.update(splits[0].endsWith("_ACK"), updateTx = splits[1], settleTx = splits[2])
              updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
            } else {
              val (address, triggerTx, settlementTx) = splits
              counterPartyAddress = address
              triggerTxStatus += ", received back"
              settlementTxStatus += ", received back"
              progressStep++
              channel = channel!!.commitFund(fundingTxId, "auto", counterPartyAddress, triggerTx, settlementTx)
              fundingTxStatus += ", signed and posted!"
              progressStep++
            }
          }.onCompletion {
            console.log("completed")
          }.launchIn(scope)
        }
      }
    }) {
      Text("Initiate!")
    }
  }
  Br()
  Button({
    onClick {
      showFundScanner = !showFundScanner
    }
    style {
      if (showFundScanner) border(style = LineStyle.Inset)
    }
  }) {
    Text("Scan QR code")
  }
  Br()
  if (showFundScanner) {
    Video({
      id("fundChannelVideo")
      style {
        width(500.px)
        height(500.px)
        property("pointer-events", "none")
      }
    })
    DisposableEffect("fundChannelVideo") {
      val video = document.getElementById("fundChannelVideo").also { console.log("video", it) } as HTMLVideoElement
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
      onDispose {
        qrScanner?.stop()
      }
    }
  }
}