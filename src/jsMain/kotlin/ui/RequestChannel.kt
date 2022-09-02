package ui

import androidx.compose.runtime.*
import channelUpdate
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import eltooScript
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
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Video
import org.w3c.dom.HTMLCanvasElement
import publish
import scope
import signAndExportTx
import subscribe
import triggerScript

@Composable
fun RequestChannel() {
  var showJoinChannel by remember { mutableStateOf(false) }
  var myTriggerKey by remember { mutableStateOf("") }
  var myUpdateKey by remember { mutableStateOf("") }
  var mySettleKey by remember { mutableStateOf("") }
  var otherTriggerKey by remember { mutableStateOf("") }
  var otherUpdateKey by remember { mutableStateOf("") }
  var otherSettleKey by remember { mutableStateOf("") }
  var triggerTxStatus by remember { mutableStateOf("") }
  var updateTxStatus by remember { mutableStateOf("") }
  var settlementTxStatus by remember { mutableStateOf("") }
  var triggerTransactionId by remember { mutableStateOf<Int?>(null) }
  var updateTransactionId by remember { mutableStateOf<Int?>(null) }
  var settlementTransactionId by remember { mutableStateOf<Int?>(null) }
  var timeLock by remember { mutableStateOf(10) }
  var channelBalance by remember { mutableStateOf(ZERO to ZERO) }
  var settlementTransaction by remember { mutableStateOf<JsonObject?>(null) }
  var myAddress by remember { mutableStateOf("") }
  var counterPartyAddress by remember { mutableStateOf("") }
  
  Button({
    onClick {
      showJoinChannel = !showJoinChannel
      val canvas = document.getElementById("joinChannelQR") as HTMLCanvasElement
      if(showJoinChannel) scope.launch {
        myTriggerKey = newKey()
        myUpdateKey = newKey()
        mySettleKey = newKey()
        triggerTxStatus = ""
        settlementTxStatus= ""
        QRCode.toCanvas(canvas, "$myTriggerKey;$myUpdateKey;$mySettleKey", { error ->
          if (error != null) console.error(error)
          else {
            console.log("qr generated, subscribing to $myTriggerKey;$myUpdateKey;$mySettleKey")
            subscribe(channelKey(myTriggerKey, myUpdateKey, mySettleKey)).onEach { msg ->
              console.log("tx msg", msg)
              val splits = msg.split(";")
              if (splits[0].startsWith("TXN_UPDATE")) {
                val (updateTxId, settleTxPair) = channelUpdate(splits[0].endsWith("ACK"), splits[1], splits[2], myUpdateKey, mySettleKey, channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey))
                updateTransactionId = updateTxId
                updateTxStatus += "Update transaction ${if (splits[0].endsWith("ACK")) "ack " else ""}received. "
                settlementTransactionId = settleTxPair.first
                settlementTransaction = settleTxPair.second
                val outputs = settleTxPair.second["outputs"]!!.jsonArray.map { json.decodeFromJsonElement<Output>(it) }
                channelBalance = outputs.find { it.miniaddress == myAddress }!!.amount to outputs.find { it.miniaddress == counterPartyAddress }!!.amount
              } else {
                timeLock = splits[0].toInt()
                otherTriggerKey = splits[1]
                otherUpdateKey = splits[2]
                otherSettleKey = splits[3]
                val triggerTx = splits[4]
                val settlementTx = splits[5]
                triggerTransactionId = newTxId().also { triggerTxId ->
                  importTx(triggerTxId, triggerTx)
                  val signedTriggerTx = signAndExportTx(triggerTxId, myTriggerKey)
                  triggerTxStatus = "Trigger transaction receved, signed"
                  settlementTransactionId = newTxId().also { settlementTxId ->
                    settlementTransaction = importTx(settlementTxId, settlementTx).also {
                      val output = json.decodeFromJsonElement<Output>(it["outputs"]!!.jsonArray.first())
                      channelBalance = ZERO to output.amount
                      counterPartyAddress = output.miniaddress
                    }
                    val signedSettlementTx = signAndExportTx(settlementTxId, mySettleKey)
                    settlementTransactionId = settlementTxId
                    settlementTxStatus = "Settlement transaction receved, signed"
                    multisigScriptAddress = deployScript(triggerScript(otherTriggerKey, myTriggerKey))
                    eltooScriptAddress = deployScript(eltooScript(timeLock, otherUpdateKey, myUpdateKey, otherSettleKey, mySettleKey))
                    myAddress = getAddress()
                    publish(
                      channelKey(otherTriggerKey, otherUpdateKey, otherSettleKey),
                      listOf(myAddress, signedTriggerTx, signedSettlementTx).joinToString(";")
                    )
                    triggerTxStatus += ", sent back"
                    settlementTxStatus += ", sent back"
                  }
                }
              }
            }.onCompletion {
              console.log("completed")
            }.launchIn(scope)
          }
        })
      }
    }
    style {
      if (showJoinChannel) border(style = LineStyle.Inset)
    }
  }) {
    Text("Request payment channel")
  }
  if (showJoinChannel) {
    Br()
    Text("Trigger key: $myTriggerKey")
    Br()
    Text("Update key: $myUpdateKey")
    Br()
    Text("Settlement key: $mySettleKey")
    Br()
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
  }
  Br()
  Canvas({
    id("joinChannelQR")
    style {
      if (!showJoinChannel) display(DisplayStyle.None)
    }
  })
  if(showJoinChannel) {
    Br()
    Text("Scan QR code on counter party device")
  }
  Br()
  Video({
    id("joinChannelVideo")
    style {
      if (!showJoinChannel) display(DisplayStyle.None)
      width(500.px)
      height(500.px)
    }
  })
}