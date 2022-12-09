package ui

import Channel
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
  
  var myKeys by remember { mutableStateOf(Channel.Keys("", "", "")) }
  var theirKeys by remember { mutableStateOf(Channel.Keys("", "", "")) }
  var theirAddress by remember { mutableStateOf("") }
  var timeLock by remember { mutableStateOf(10) }
  
  var fundingTxStatus by remember { mutableStateOf("") }
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  
  var showFundScanner by remember { mutableStateOf(false) }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  var progressStep: Int by remember { mutableStateOf(0) }
  
  var channel by remember { mutableStateOf<Channel?>(null) }
  
  LaunchedEffect("fundChannel") {
    newKeys(3).apply {
      myKeys = Channel.Keys(this[0], this[1], this[2])
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
    Text("My trigger key: ${myKeys.trigger}")
    Br()
    Text("My update key: ${myKeys.update}")
    Br()
    Text("My settlement key: ${myKeys.settle}")
    Br()
    Text("Counterparty trigger key:")
    TextInput(theirKeys.trigger) {
      onInput {
        theirKeys = theirKeys.copy(trigger = it.value)
      }
      style {
        width(500.px)
      }
    }
    Br()
    Text("Counterparty update key:")
    TextInput(theirKeys.update) {
      onInput {
        theirKeys = theirKeys.copy(update = it.value)
      }
      style {
        width(500.px)
      }
    }
    Br()
    Text("Counterparty settlement key:")
    TextInput(theirKeys.settle) {
      onInput {
        theirKeys = theirKeys.copy(settle = it.value)
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
  if (listOf(myKeys.trigger, myKeys.update, myKeys.settle, theirKeys.trigger, theirKeys.update, theirKeys.settle).all(String::isNotEmpty)
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
          val (channelNotPosted, fundingTxId) = prepareFundChannel(myKeys, theirKeys, amount, tokenId, timeLock) {
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
        
          subscribe(myKeys).onEach { msg ->
            console.log("tx msg", msg)
            val splits = msg.split(";")
            if (splits[0].startsWith("TXN_UPDATE")) {
              channel = channel!!.update(splits[0].endsWith("_ACK"), updateTx = splits[1], settleTx = splits[2])
              updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
            } else {
              val (address, triggerTx, settlementTx) = splits
              theirAddress = address
              triggerTxStatus += " and received back."
              settlementTxStatus += " and received back."
              progressStep++
              channel = channel!!.commitFund(fundingTxId, "auto", theirAddress, triggerTx, settlementTx)
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
          theirKeys = Channel.Keys(this[0], this[1], this[2])
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