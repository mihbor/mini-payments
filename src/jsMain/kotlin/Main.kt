import kotlinx.coroutines.MainScope
import minima.MDS
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposableInBody
import ui.*

val scope = MainScope()
external fun require(module: String): dynamic

fun main() {
  init()
  
  renderComposableInBody {
    A("minipay.apk") {
      Text("Download standalone Android app")
    }
    Br()
    Text("UID: ${MDS.minidappuid}")
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