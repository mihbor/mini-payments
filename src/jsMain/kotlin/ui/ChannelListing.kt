package ui

import ChannelState
import androidx.compose.runtime.*
import blockNumber
import eltooScriptCoins
import getChannels
import kotlinx.coroutines.launch
import ltd.mbor.minimak.MDS
import ltd.mbor.minimak.getCoins
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.dom.*
import scope

@Composable
fun ChannelListing() {
  var showChannels by remember { mutableStateOf(false) }
  val channels = remember { mutableStateListOf<ChannelState>() }

  Div({
    classes(StyleSheets.container)
  }) {
    Button({
      onClick {
        showChannels = !showChannels
        if (showChannels) loadChannels(channels)
      }
      style {
        if (showChannels) border(style = LineStyle.Inset)
      }
    }) {
      Text("Channel listing")
    }
    if (showChannels) {
      Button({
        onClick {
          loadChannels(channels)
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
  }
}

private fun loadChannels(channels: MutableList<ChannelState>) {
  scope.launch {
    val newChannels = getChannels()
    channels.clear()
    channels.addAll(newChannels)
    newChannels.forEach {
      eltooScriptCoins.put(it.eltooAddress, MDS.getCoins(address = it.eltooAddress))
    }
  }
}