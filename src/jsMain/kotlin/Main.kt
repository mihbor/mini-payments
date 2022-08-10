import kotlinx.coroutines.MainScope
import org.jetbrains.compose.web.dom.Br
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
    Receive()
    Br()
    Send()
    Br()
    FundChannel()
    Br()
    JoinChannel()
  }
}