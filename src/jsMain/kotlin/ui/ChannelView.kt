package ui

import Channel
import androidx.compose.runtime.Composable
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import logic.balances
import logic.blockNumber
import ltd.mbor.minimak.Balance
import ltd.mbor.minimak.Coin
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text

@Composable
fun ChannelView(
  channel: Channel,
  multisigScriptBalances: List<Balance>,
  eltooScriptCoins: List<Coin>,
  updateChannel: (Channel) -> Unit
) {
  Br()
  multisigScriptBalances.firstOrNull{ it.tokenId == channel.tokenId }?.let{
    TokenIcon(it.tokenId, balances)
    Text("${it.tokenName} token funding balance: ${it.confirmed.toPlainString()}")
    Br()
  }
  if (multisigScriptBalances.any { it.unconfirmed > ZERO || it.confirmed > ZERO }) {
    Text("Channel balance: me ${channel.my.balance.toPlainString()}, counterparty ${channel.their.balance.toPlainString()}")
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