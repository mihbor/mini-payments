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
//  tokenId?.let { TokenIcon(tokenId, tokens) }
  Select({
    if (disabled) disabled()
    onChange {
      setTokenId(it.value!!)
    }
  }) {
    balances.forEach { balance ->
      key(balance.tokenid) {
        Option(balance.tokenid, { if (balance.tokenid == tokenId) selected() }) {
          Text("${balance.token?.name ?: "Minima"} (${balance.sendable.toPlainString()})")
        }
      }
    }
  }
}