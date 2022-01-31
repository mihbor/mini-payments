import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Text

@Composable
fun TokenSelect(tokenId: String, setTokenId: (String) -> Unit) {
  Select({
    onChange {
      setTokenId(it.value!!)
    }
  }) {
    balances.forEach { balance ->
      key(balance.tokenid) {
        Option(balance.tokenid, { if (balance.tokenid == tokenId) selected() }) {
          Text("${balance.token} (${balance.sendable.toPlainString()})")
        }
      }
    }
  }
}