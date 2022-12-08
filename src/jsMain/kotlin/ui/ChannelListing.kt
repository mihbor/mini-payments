package ui

import ChannelState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import logic.balances
import logic.blockNumber
import logic.eltooScriptCoins
import logic.getChannels
import ltd.mbor.minimak.MDS
import ltd.mbor.minimak.getCoins
import org.jetbrains.compose.web.dom.*
import scope

@Composable
fun ChannelListing() {
  val channels = remember { mutableStateListOf<ChannelState>() }

  LaunchedEffect("channels") { loadChannels(channels) }
  Button({
    onClick {
      scope.launch {
        loadChannels(channels)
      }
    }
  }) {
    Text("Refresh")
  }
  Table {
    Thead {
      Tr {
        Th { Text("ID") }
        Th { Text("Status") }
        Th { Text("Sequence number") }
        Th { Text("Token") }
        Th { Text("My balance") }
        Th { Text("Their balance") }
        Th { Text("Actions") }
      }
    }
    Tbody {
      channels.forEachIndexed { index, channel ->
        Tr {
          Td { Text(channel.id.toString()) }
          Td { Text(channel.status) }
          Td { Text(channel.sequenceNumber.toString()) }
          Td {
            TokenIcon(channel.tokenId, balances)
            Text(balances[channel.tokenId]?.tokenName ?: channel.tokenId)
          }
          Td { Text(channel.myBalance.toPlainString()) }
          Td { Text(channel.counterPartyBalance.toPlainString()) }
          Td {
            if (channel.status == "OPEN") {
              ChannelTransfers(channel)
              Br()
            }
            Settlement(channel, blockNumber, eltooScriptCoins[channel.eltooAddress] ?: emptyList()) {
              channels[index] = it
            }
          }
        }
      }
    }
  }
}

suspend fun loadChannels(channels: MutableList<ChannelState>) {
  val newChannels = getChannels()
  channels.clear()
  channels.addAll(newChannels)
  newChannels.filter{ it.eltooAddress.isNotBlank() }.forEach {
    eltooScriptCoins.put(it.eltooAddress, MDS.getCoins(address = it.eltooAddress))
  }
}