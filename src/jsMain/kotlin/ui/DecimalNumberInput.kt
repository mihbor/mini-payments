package ui

import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.TextInput

@Composable
fun DecimalNumberInput(
  value: BigDecimal? = null,
  min: BigDecimal? = null,
  max: BigDecimal? = null,
  disabled: Boolean = false,
  setValue: (BigDecimal?) -> Unit = {},
) {
  var text by remember { mutableStateOf(value?.toString() ?: "") }
  value.takeUnless { it == text.toBigDecimalOrNull() || it == ZERO && text.isEmpty()} ?.let { text = it.toPlainString() }
  TextInput(text) {
    if (disabled) this.disabled()
    onInput {
      if (it.value.isEmpty()) setValue(min?.takeIf { it > ZERO } ?: ZERO)
      else it.value.toBigDecimalOrNull()?.let { setValue(it) }
      text = it.value
    }
  }
}

fun String.toBigDecimalOrNull(): BigDecimal? {
  return try {
    toBigDecimal()
  } catch (e: Exception) {
    null
  }
}
