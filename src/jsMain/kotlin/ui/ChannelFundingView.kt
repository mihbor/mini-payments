package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.launch
import minima.Balance
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import post
import scope

@Composable
fun ChannelFundingView(
  isFunded: Boolean,
  multisigScriptBalances: List<Balance>,
  eltooScriptBalances: List<Balance>,
  triggerTxId: Int,
  settleTxId: Int
) {
  var settlementTriggered by remember { mutableStateOf(false) }
  var settlementCompleted by remember { mutableStateOf(false) }
  if (isFunded) {
    Text("Channel funded!")
    Br()
    multisigScriptBalances.forEach {
      Text("[${it.tokenid}] token funding balance: ${it.confirmed.toPlainString()}")
      Br()
    }
    if (multisigScriptBalances.any { it.confirmed > BigDecimal.ZERO }) {
      Button({
        onClick {
          scope.launch { post(triggerTxId) }
          settlementTriggered = true
        }
        if (settlementTriggered) disabled()
      }) {
        Text("Trigger settlement!")
      }
      Br()
      if (settlementTriggered) {
        eltooScriptBalances.forEach {
          Text("[${it.tokenid}] token eltoo balance: ${it.confirmed.toPlainString()}")
          Br()
        }
      }
      if (eltooScriptBalances.any { it.confirmed > BigDecimal.ZERO }) {
        Button({
          onClick {
            scope.launch { post(settleTxId) }
            settlementCompleted = true
          }
          if (settlementCompleted) disabled()
        }) {
          Text("Complete settlement!")
        }
      }
    }
  }
}