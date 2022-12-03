package ui

import androidx.compose.runtime.*
import kotlinx.browser.document
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.Color.black
import org.jetbrains.compose.web.css.Color.white
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Hr
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

tailrec fun Element?.itOrAncestorMatches(predicate: (HTMLElement) -> Boolean): Boolean {
  return if (this !is HTMLElement) false
    else if (predicate(this)) true
    else parentElement.itOrAncestorMatches(predicate)
}

@Composable
fun Menu(setView: (String) -> Unit) {
  var showMenu by remember { mutableStateOf(false) }
  val dismissMenu: (Event) -> Unit = { event ->
    if(!(event.target as? HTMLElement).itOrAncestorMatches { it.id == "menu" }) {
      showMenu = false
    }
  }
  Div{
    Span({
      onClick {
        showMenu = !showMenu
      }
    }) {
      Text("☰")
    }
    Text("MiniPay")
  }
  if (showMenu) {
    DisposableEffect("menu") {
      document.addEventListener("click", dismissMenu)
      onDispose {
        document.removeEventListener("click", dismissMenu)
      }
    }
    Div({
      id("menu")
      style {
        padding(10.px)
        backgroundColor(white)
        border(1.px, LineStyle.Solid, black)
        position(Position.Fixed)
        top(30.px)
        left(0.px)
        property("z-index", 1)
      }
    }) {
      Div({
        onClick {
          setView("receive")
          showMenu = false
        }
      }) {
        Text("Receive")
      }
      Hr()
      Div({
        onClick {
          setView("send")
          showMenu = false
        }
      }) {
        Text("Send")
      }
      Hr()
      Div({
        onClick {
          setView("request channel")
          showMenu = false
        }
      }) {
        Text("Request channel")
      }
      Hr()
      Div({
        onClick {
          setView("fund channel")
          showMenu = false
        }
      }) {
        Text("Fund channel")
      }
      Hr()
      Div({
        onClick {
          setView("channels")
          showMenu = false
        }
      }) {
        Text("List channels")
      }
      Hr()
      Div({
        onClick {
          setView("settings")
          showMenu = false
        }
      }) {
        Text("Settings")
      }
    }
  }
}