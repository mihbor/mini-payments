package ui

import ChannelState
import androidx.compose.runtime.Composable
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import logic.blockNumber
import ltd.mbor.minimak.Balance
import ltd.mbor.minimak.Coin
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text

@Composable
fun ChannelView(
  channel: ChannelState,
  multisigScriptBalances: List<Balance>,
  eltooScriptCoins: List<Coin>,
  updateChannel: (ChannelState) -> Unit
) {
  Br()
  multisigScriptBalances.forEach {
    Text("[${it.tokenId}] token funding balance: ${it.confirmed.toPlainString()}")
    Br()
  }
  if (multisigScriptBalances.any { it.unconfirmed > ZERO || it.confirmed > ZERO }) {
    Text("Channel balance: me ${channel.myBalance.toPlainString()}, counterparty ${channel.counterPartyBalance.toPlainString()}")
    ChannelTransfers(channel)
    Br()
  }
  Settlement(
    channel.copy(status = if (multisigScriptBalances.any { it.unconfirmed > ZERO || it.confirmed > ZERO }) "OPEN" else channel.status),
    blockNumber,
    eltooScriptCoins,
    updateChannel
  )
}