import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposableInBody
import ui.*

val scope = MainScope()
external fun require(module: String): dynamic

fun main() {
  scope.launch {
    init(getParams("uid"))
  }
  renderComposableInBody {
    A("minipay.apk") {
      Text("Download standalone Android app")
    }
    Br()
    Text("UID: ${getParams("uid")}")
    Br()
    Receive()
    Br()
    Send()
    Br()
    FundChannel()
    Br()
    RequestChannel()
    Br()
    ChannelListing()
  }
}