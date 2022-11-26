package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import balances
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text

@Composable
fun TokenSelect(tokenId: String, disabled: Boolean = false, setTokenId: (String) -> Unit) {
  Select({
    if (disabled) disabled()
    onChange {
      setTokenId(it.value!!)
    }
  }) {
    balances.values.forEach { balance ->
      key(balance.tokenId) {
        Option(balance.tokenId, { if (balance.tokenId == tokenId) selected() }) {
          Text("${balance.tokenName ?: "Minima"} (${balance.sendable.toPlainString()})")
        }
      }
    }
  }
}