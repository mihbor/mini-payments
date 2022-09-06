package ui

import ChannelState
import androidx.compose.runtime.Composable
import blockNumber
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import minima.Balance
import minima.Coin
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text

@Composable
fun ChannelView(
  channel: ChannelState,
  multisigScriptBalances: List<Balance>,
  eltooScriptCoins: List<Coin>,
) {
  if (channel.status != "OFFERED") {
    Br()
    multisigScriptBalances.forEach {
      Text("[${it.tokenid}] token funding balance: ${it.confirmed.toPlainString()}")
      Br()
    }
    if (multisigScriptBalances.any { it.unconfirmed > ZERO || it.confirmed > ZERO }) {
      Text("Channel balance: me ${channel.myBalance.toPlainString()}, counterparty ${channel.counterPartyBalance.toPlainString()}")
      ChannelTransfers(channel)
      Br()
      Settlement(channel, blockNumber, eltooScriptCoins)
    }
  }
}