package ui

import androidx.compose.runtime.*
import channelUpdate
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import commitFundChannel
import eltooScript
import externals.QrScanner
import fundingTx
import kotlinx.browser.document
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import minima.*
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

var multisigScriptAddress by mutableStateOf("")
var eltooScriptAddress by mutableStateOf("")
val multisigScriptBalances = mutableStateListOf<Balance>()
val eltooScriptCoins = mutableStateListOf<Coin>()

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
  var triggerTransactionId by remember { mutableStateOf<Int?>(null) }
  var updateTransactionId by remember { mutableStateOf<Int?>(null) }
  var settlementTransactionId by remember { mutableStateOf<Int?>(null) }
  var channelBalance by remember { mutableStateOf(ZERO to ZERO) }
  var settlementTransaction by remember { mutableStateOf<JsonObject?>(null) }
  var myAddress by remember { mutableStateOf("") }
  var counterPartyAddress by remember { mutableStateOf("") }
  
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
    triggerTransactionId?.let { trigger -> settlementTransactionId?.let{ settle ->
      ChannelView(
        multisigScriptAddress.isNotEmpty(),
        timeLock,
        multisigScriptBalances,
        channelBalance,
        myAddress,
        myUpdateKey,
        mySettleKey,
        counterPartyAddress,
        channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
        settlementTransaction!!,
        eltooScriptCoins,
        {
          post(trigger)
          triggerTxStatus += " and posted!"
        },
        {
          post(settle)
          settlementTxStatus += " and posted!"
        },
        updateTransactionId?.let {
          {
            post(it)
            updateTxStatus += " Posted!"
          }
        }
      )
    }}
    if (listOf(myTriggerKey, mySettleKey, myUpdateKey, otherTriggerKey, otherSettleKey, otherUpdateKey).all(String::isNotEmpty)) {
      DecimalNumberInput(amount, min = ZERO, disabled = fundingTxStatus.isNotEmpty()) {
        it?.let { amount = it }
      }
      TokenSelect(tokenId, fundingTxStatus.isNotEmpty()) {
        tokenId = it
      }
      Text("Update only time lock (block diff)")
      NumberInput(timeLock, min = 0) {
        if (fundingTxStatus.isNotEmpty()) disabled()
        onInput {
          timeLock = it.value!!.toInt()
        }
      }
      Button({
        if(amount <= 0 || fundingTxStatus.isNotEmpty()) disabled()
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
            val channelId = prepareFundChannel(otherTriggerKey, otherUpdateKey, otherSettleKey, timeLock, myTriggerKey, myUpdateKey, mySettleKey, exportedTriggerTx, exportedSettlementTx, amount)
            console.log("channelId", channelId)
            triggerTxStatus += ", sent"
            settlementTxStatus += ", sent"
  
            console.log("subscribing to", "$myTriggerKey;$myUpdateKey;$mySettleKey")
            subscribe(channelKey(myTriggerKey, myUpdateKey, mySettleKey)).onEach { msg ->
              console.log("tx msg", msg)
              val splits = msg.split(";")
              if (splits[0].startsWith("TXN_UPDATE")) {
                val (updateTxId, settleTxPair) = channelUpdate(splits[0].endsWith("_ACK"), splits[1], splits[2], myUpdateKey, mySettleKey, channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey))
                updateTransactionId = updateTxId
                updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
                settlementTransactionId = settleTxPair.first
                settlementTransaction = settleTxPair.second
                val outputs = settleTxPair.second["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Output>(it) }
                channelBalance = outputs.find { it.miniaddress == myAddress }!!.amount to outputs.find { it.miniaddress == counterPartyAddress }!!.amount
              } else {
                val (address, triggerTx, settlementTx) = splits
                counterPartyAddress = address
                triggerTransactionId = newTxId().also {
                  importTx(it, triggerTx)
                }
                triggerTxStatus += ", received back"
                settlementTransactionId = newTxId().also {
                  settlementTransaction = importTx(it, settlementTx)
                }
                settlementTxStatus += ", received back"
                commitFundChannel(channelId, fundingTxId, "auto")
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