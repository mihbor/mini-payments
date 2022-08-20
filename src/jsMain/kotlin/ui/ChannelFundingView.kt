package ui

import androidx.compose.runtime.*
import blockNumber
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.launch
import minima.Balance
import minima.Coin
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import scope

@Composable
fun ChannelFundingView(
  isFunded: Boolean,
  timeLock: Int,
  multisigScriptBalances: List<Balance>,
  eltooScriptCoins: List<Coin>,
  trigger: suspend () -> Unit,
  complete: suspend () -> Unit
) {
  var settlementTriggered by remember { mutableStateOf(false) }
  var settlementCompleted by remember { mutableStateOf(false) }
  if (isFunded) {
    Br()
    multisigScriptBalances.forEach {
      Text("[${it.tokenid}] token funding balance: ${it.confirmed.toPlainString()}")
      Br()
    }
    if (multisigScriptBalances.any { it.confirmed > BigDecimal.ZERO }) {
      Button({
        onClick {
          settlementTriggered = true
          scope.launch {
            trigger()
            settlementTriggered = false
          }
        }
        if (settlementTriggered) disabled()
      }) {
        Text("Trigger settlement!")
      }
      Br()
      eltooScriptCoins.forEach {
        Text("[${it.tokenid}] token eltoo coin: ${it.tokenamount?.toPlainString() ?: it.amount.toPlainString()} timelock ${
          (it.created.toInt() + timeLock - blockNumber).takeIf { it > 0 }?.let { "ends in $it blocks" } ?: "ended"}")
        Br()
      }
      if (eltooScriptCoins.isNotEmpty()) {
        Button({
          onClick {
            settlementCompleted = true
            scope.launch {
              complete()
              settlementCompleted = false
            }
          }
          if (settlementCompleted) disabled()
        }) {
          Text("Complete settlement!")
        }
      }
    }
  }
}