package ui

import ChannelState
import androidx.compose.runtime.*
import channelUpdate
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import commitFundChannel
import eltooScript
import eltooScriptAddress
import eltooScriptCoins
import externals.QrScanner
import fundingTx
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import minima.*
import minima.State
import multisigScriptAddress
import multisigScriptBalances
import newTxId
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLVideoElement
import prepareFundChannel
import scope
import signFloatingTx
import subscribe
import triggerScript
import updateChannelBalance

@Composable
fun FundChannel() {
  var showFundChannel by remember { mutableStateOf(false) }
  var showFundScanner by remember { mutableStateOf(false) }
  var amount by remember { mutableStateOf(ZERO) }
  var tokenId by remember { mutableStateOf("0x00") }
  var timeLock by remember { mutableStateOf(10) }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var otherTriggerKey by remember { mutableStateOf("") }
  var otherUpdateKey by remember { mutableStateOf("") }
  var otherSettleKey by remember { mutableStateOf("") }
  var qrScanner: QrScanner? by remember { mutableStateOf(null) }
  var fundingTxStatus by remember { mutableStateOf("") }
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  var channelBalance by remember { mutableStateOf(ZERO to ZERO) }
  var myAddress by remember { mutableStateOf("") }
  var counterPartyAddress by remember { mutableStateOf("") }
  var channel by remember { mutableStateOf<ChannelState?>(null) }
  
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
        fundingTxStatus = ""
        triggerTxStatus = ""
        settlementTxStatus= ""

      } else {
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
    if(fundingTxStatus.isEmpty()) {
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
          width(400.px)
        }
      }
      Br()
      Text("Counterparty update key:")
      TextInput(otherUpdateKey) {
        onInput {
          otherUpdateKey = it.value
        }
        style {
          width(400.px)
        }
      }
      Br()
      Text("Counterparty settlement key:")
      TextInput(otherSettleKey) {
        onInput {
          otherSettleKey = it.value
        }
        style {
          width(400.px)
        }
      }
      Br()
    }
    fundingTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    triggerTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    updateTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    settlementTxStatus.takeUnless { it.isEmpty() }?.let{
      Text(it)
      Br()
    }
    channel?.let {
      ChannelView(it, multisigScriptBalances, eltooScriptCoins[it.eltooAddress] ?: emptyList())
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
        if(amount <= 0) disabled()
        onClick {
          showFundScanner = false
          qrScanner?.stop()
          scope.launch {
            multisigScriptAddress = deployScript(triggerScript(myTriggerKey, otherTriggerKey))
            eltooScriptAddress = deployScript(eltooScript(timeLock, myUpdateKey, otherUpdateKey, mySettleKey, otherSettleKey))
            console.log("multisig address (fund)", multisigScriptAddress)
            val (fundingTxId, fundingTx) = fundingTx(multisigScriptAddress, amount, tokenId)
            channelBalance = amount to ZERO
            fundingTxStatus = "Funding transaction created"
            myAddress = getAddress()
            val (triggerTxId, triggerTx) = signFloatingTx(myTriggerKey, multisigScriptAddress, eltooScriptAddress, fundingTx, mapOf(99 to "0"))
            triggerTxStatus = "Trigger transaction created, signed"
            val (settlementTxId, _) = signFloatingTx(mySettleKey, eltooScriptAddress, myAddress, triggerTx, mapOf(99 to "0"))
            settlementTxStatus = "Settlement transaction created, signed"
            val exportedTriggerTx = exportTx(triggerTxId)
            val exportedSettlementTx = exportTx(settlementTxId)
            channel = prepareFundChannel(
              myTriggerKey, myUpdateKey, mySettleKey,
              otherTriggerKey, otherUpdateKey, otherSettleKey,
              myAddress, multisigScriptAddress, eltooScriptAddress,
              exportedTriggerTx, exportedSettlementTx, amount, timeLock
            )
            console.log("channelId", channel!!.id)
            triggerTxStatus += ", sent"
            settlementTxStatus += ", sent"
  
            console.log("subscribing to", "$myTriggerKey;$myUpdateKey;$mySettleKey")
            subscribe(channelKey(myTriggerKey, myUpdateKey, mySettleKey)).onEach { msg ->
              console.log("tx msg", msg)
              val splits = msg.split(";")
              if (splits[0].startsWith("TXN_UPDATE")) {
                val updateTx = splits[1]
                val settleTx = splits[2]
                val settleTxPair = channelUpdate(splits[0].endsWith("_ACK"), updateTx, settleTx, myUpdateKey, mySettleKey, channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey))
                updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
                val outputs = settleTxPair.second["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Output>(it) }
                channelBalance = outputs.find { it.miniaddress == myAddress }!!.amount to outputs.find { it.miniaddress == counterPartyAddress }!!.amount
                val sequenceNumber = settleTxPair.second["state"]!!.jsonArray.map { json.decodeFromJsonElement<State>(it) }.find { it.port == "99" }?.data?.toInt()
                channel = updateChannelBalance(channel!!, channelBalance, sequenceNumber!!, updateTx, settleTx)
              } else {
                val (address, triggerTx, settlementTx) = splits
                counterPartyAddress = address
                importTx(newTxId(), triggerTx)
                triggerTxStatus += ", received back"
                importTx(newTxId(), settlementTx)
                settlementTxStatus += ", received back"
                channel = commitFundChannel(channel!!, fundingTxId, "auto", counterPartyAddress, settlementTx)
                fundingTxStatus += " and posted!"
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