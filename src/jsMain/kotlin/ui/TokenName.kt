package ui

import androidx.compose.runtime.Composable
import minima.Token
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Text

@Composable
fun TokenIcon(token: Token) {
  (token.icon?.takeIf { it.isNotBlank() } ?: "minima.svg".takeIf { token.tokenid == "0x00" })?.let {
    Img(it) {
      style {
        width(16.px)
        height(16.px)
        property("vertical-align", "middle")
      }
    }
  }
}

@Composable
fun TokenIcon(tokenId: String, tokens: Map<String, Token>) {
  tokens[tokenId]?.let { token ->
    TokenIcon(token)
  }
}

@Composable
fun TokenName(tokenId: String, tokens: Map<String, Token>) {
  val token = tokens[tokenId]
  if (token != null) {
    TokenIcon(token)
    Text(" ${token.token}")
  } else {
    Text(tokenId)
  }
}