import com.ionspin.kotlin.bignum.serialization.kotlinx.bigdecimal.bigDecimalHumanReadableSerializerModule
import kotlinx.coroutines.MainScope
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.renderComposableInBody
import ui.Receive
import ui.Send

val scope = MainScope()
val json = Json {
  serializersModule = bigDecimalHumanReadableSerializerModule
}
external fun require(module: String): dynamic

fun main() {
  init()
  
  renderComposableInBody {
    Receive()
    Br()
    Send()
  }
}