package ui

import androidx.compose.runtime.*
import blockNumber
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import minima.Balance
import minima.Coin
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text
import scope

@Composable
fun ChannelView(
  isFunded: Boolean,
  timeLock: Int,
  multisigScriptBalances: List<Balance>,
  channelBalance: Pair<BigDecimal, BigDecimal>,
  myAddress: String,
  myUpdateKey: String,
  mySettleKey: String,
  counterPartyAddress: String,
  channelKey: String,
  currentSettlementTx: JsonObject,
  eltooScriptCoins: List<Coin>,
  trigger: suspend () -> Unit,
  complete: suspend () -> Unit,
  update: (suspend () -> Unit)? = null,
) {
  var settlementTriggering by remember { mutableStateOf(false) }
  var settlementCompleting by remember { mutableStateOf(false) }
  var updatePosting by remember { mutableStateOf(false) }
  if (isFunded) {
    Br()
    multisigScriptBalances.forEach {
      Text("[${it.tokenid}] token funding balance: ${it.confirmed.toPlainString()}")
      Br()
    }
    if (multisigScriptBalances.any { it.unconfirmed > ZERO || it.confirmed > ZERO }) {
      ChannelTransfers(channelBalance, myAddress, myUpdateKey, mySettleKey, counterPartyAddress, channelKey, currentSettlementTx)
      Button({
        onClick {
          settlementTriggering = true
          scope.launch {
            trigger()
            settlementTriggering = false
          }
        }
        if (settlementTriggering) disabled()
      }) {
        Text("Trigger settlement!")
      }
      Br()
    }
    eltooScriptCoins.forEach {
      Text("[${it.tokenid}] token eltoo coin: ${it.tokenamount?.toPlainString() ?: it.amount.toPlainString()} timelock ${
        (it.created.toInt() + timeLock - blockNumber).takeIf { it > 0 }?.let { "ends in $it blocks" } ?: "ended"}")
      Br()
    }
    if (eltooScriptCoins.isNotEmpty()) {
      if (update != null) Button({
        onClick {
          updatePosting = true
          scope.launch {
            update()
            updatePosting = false
          }
        }
        if (updatePosting) disabled()
      }) {
        Text("Post latest update")
      }
      Button({
        if (settlementCompleting || updatePosting || eltooScriptCoins.any { it.created.toInt() + timeLock > blockNumber }) disabled()
        onClick {
          settlementCompleting = true
          scope.launch {
            complete()
            settlementCompleting = false
          }
        }
      }) {
        Text("Complete settlement!")
      }
    }
  }
}