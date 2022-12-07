import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import logic.getParams
import logic.init
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
    var view by remember { mutableStateOf("Settings") }
    Style(StyleSheets)
    Menu(view) { view = it }
    when(view) {
      "Receive" -> Receive()
      "Send" -> Send()
      "Fund channel" -> FundChannel()
      "Request channel" -> RequestChannel()
      "Channels" -> ChannelListing()
      "Settings" -> Settings()
    }
  }
}