package ui

import androidx.compose.runtime.Composable
import getParams
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
fun Header() {
  Div({
    classes(StyleSheets.container)
  }) {
    A("minipay.apk") {
      Text("Download standalone Android app")
    }
    Br()
    Text("UID: ${getParams("uid")}")
  }
}