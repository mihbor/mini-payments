package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import scope
import sendViaChannel
import store

@Composable
fun ChannelTransfers(
  channelBalance: Pair<BigDecimal, BigDecimal>,
  myAddress: String,
  myUpdateKey: String,
  mySettleKey: String,
  counterPartyAddress: String,
  channelKey: String,
  currentSettlementTx: JsonObject
) {
  
  Text("Channel balance: me ${channelBalance.first.toPlainString()}, counterparty ${channelBalance.second.toPlainString()}")
  if (channelBalance.first > ZERO) {
    var amount by remember { mutableStateOf(ZERO) }
    DecimalNumberInput(amount, min = ZERO, max = channelBalance.first) { it?.let { amount = it } }
    Button({
      onClick {
        scope.launch {
          val txPair = sendViaChannel(amount, channelBalance, myAddress, myUpdateKey, mySettleKey, counterPartyAddress, currentSettlementTx)
          store(channelKey, with(txPair) { "$first;$second" })
        }
      }
    }) {
      Text("Send via channel")
    }
    Br()
  }
  if (channelBalance.second > ZERO) {
    var amount by remember { mutableStateOf(ZERO) }
    DecimalNumberInput(amount, min = ZERO, max = channelBalance.second) { it?.let { amount = it } }
    Button({
      onClick {
    
      }
    }) {
      Text("Request via channel")
    }
    Br()
  }
}

fun channelKey(vararg keys: String) = keys.joinToString(";")
