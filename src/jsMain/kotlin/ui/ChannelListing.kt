package ui

import ChannelState
import androidx.compose.runtime.*
import getChannels
import kotlinx.coroutines.launch
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
    }
    style {
      if (showChannels) border(style = LineStyle.Inset)
    }
  }) {
    Text("Channel listing")
  }
  if (showChannels) {
    scope.launch {
      val newChannels = getChannels()
      channels.clear()
      channels.addAll(newChannels)
    }
    Table {
      Thead {
        Tr {
          Th { Text("ID") }
          Th { Text("Status") }
          Th { Text("Sequence number") }
        }
      }
      Tbody {
        channels.forEach { channel ->
          Tr {
            Td { Text(channel.id.toString()) }
            Td { Text(channel.status) }
            Td { Text(channel.sequenceNumber.toString()) }
          }
        }
      }
    }
  }
}