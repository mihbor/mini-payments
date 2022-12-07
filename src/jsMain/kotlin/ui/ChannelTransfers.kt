package ui

import ChannelState
import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.coroutines.launch
import logic.request
import logic.send
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import scope

@Composable
fun ChannelTransfers(channel: ChannelState) {
  if (channel.myBalance > ZERO) {
    Br()
    var amount by remember { mutableStateOf(ZERO) }
    DecimalNumberInput(amount, min = ZERO, max = channel.myBalance) { it?.let { amount = it } }
    Button({
      onClick {
        scope.launch {
          channel.send(amount)
        }
      }
    }) {
      Text("Send via channel")
    }
  }
  if (channel.counterPartyBalance > ZERO) {
    Br()
    var amount by remember { mutableStateOf(ZERO) }
    DecimalNumberInput(amount, min = ZERO, max = channel.counterPartyBalance) { it?.let { amount = it } }
    Button({
      onClick {
        scope.launch {
          channel.request(amount)
        }
      }
    }) {
      Text("Request via channel")
    }
  }
}
