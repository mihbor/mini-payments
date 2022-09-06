package ui

import ChannelState
import androidx.compose.runtime.*
import blockNumber
import eltooScriptCoins
import getChannels
import kotlinx.coroutines.launch
import minima.getCoins
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.dom.*
import scope

@Composable
fun ChannelListing() {
  var showChannels by remember { mutableStateOf(false) }
  val channels = remember { mutableStateListOf<ChannelState>() }
  Button({
    onClick {
      showChannels = !showChannels
      if (showChannels) scope.launch {
        val newChannels = getChannels()
        channels.clear()
        channels.addAll(newChannels)
        newChannels.forEach {
          eltooScriptCoins.put(it.eltooAddress, getCoins(address = it.eltooAddress))
        }
      }
    }
    style {
      if (showChannels) border(style = LineStyle.Inset)
    }
  }) {
    Text("Channel listing")
  }
  if (showChannels) {
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
        channels.forEach { channel ->
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
              Settlement(channel, blockNumber, eltooScriptCoins[channel.eltooAddress] ?: emptyList())
            }
          }
        }
      }
    }
  }
}