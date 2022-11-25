import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun JsonElement?.jsonString(field: String) = this!!.jsonObject[field]!!.jsonPrimitive.content