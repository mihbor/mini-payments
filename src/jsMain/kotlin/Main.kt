import kotlinx.coroutines.MainScope
import minima.MDS
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposableInBody
import ui.FundChannel
import ui.JoinChannel
import ui.Receive
import ui.Send

val scope = MainScope()
external fun require(module: String): dynamic

fun main() {
  init()
  
  renderComposableInBody {
    Text("UID: ${MDS.minidappuid}")
    Br()
    Receive()
    Br()
    Send()
    Br()
    FundChannel()
    Br()
    JoinChannel()
  }
}