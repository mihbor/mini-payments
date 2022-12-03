import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.renderComposableInBody
import ui.*

val scope = MainScope()
external fun require(module: String): dynamic

fun main() {
  scope.launch {
    init(getParams("uid"))
  }
  renderComposableInBody {
    var view by remember { mutableStateOf("settings") }
    Style(StyleSheets)
    Menu{ view = it }
    when(view) {
      "receive" -> Receive()
      "send" -> Send()
      "fund channel" -> FundChannel()
      "request channel" -> RequestChannel()
      "channels" -> ChannelListing()
      "settings" -> Settings()
    }
  }
}