package logic

import Channel
import androidx.compose.runtime.*
import com.ionspin.kotlin.bignum.decimal.BigDecimal.Companion.ZERO
import kotlinx.browser.window
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import ltd.mbor.minimak.*
import scope
import kotlin.random.Random

fun triggerScript(triggerSig1: String, triggerSig2: String) =
  "RETURN MULTISIG(2 $triggerSig1 $triggerSig2)"

fun eltooScript(blockDiff: Int = 256, updateSig1: String, updateSig2: String, settleSig1: String, settleSig2: String) = """
LET st=STATE(99)
LET ps=PREVSTATE(99)
IF st EQ ps AND @COINAGE GT $blockDiff AND MULTISIG(2 $settleSig1 $settleSig2) THEN
RETURN TRUE
ELSEIF st GT ps AND MULTISIG(2 $updateSig1 $updateSig2) THEN
RETURN TRUE
ENDIF
"""

val balances = mutableStateMapOf<String, Balance>()
val channels = mutableStateListOf<Channel>()
var blockNumber by mutableStateOf(0)

fun newTxId() = Random.nextInt(1_000_000_000)

var multisigScriptAddress by mutableStateOf("")
var eltooScriptAddress by mutableStateOf("")
val multisigScriptBalances = mutableStateListOf<Balance>()
val eltooScriptCoins = mutableStateMapOf<String, List<Coin>>()

external fun decodeURIComponent(encodedURI: String): String

fun getParams(parameterName: String): String? {
  val items = window.location.search.takeIf { it.length > 1 }?.substring(1)?.split("&") ?: emptyList()
  return items.asSequence().mapNotNull {
    val (name, value) = it.split("=");
    if (name == parameterName) decodeURIComponent(value) else null
  }.firstOrNull()
}

suspend fun init(uid: String?) {
  MDS.init(uid ?: "0x00", window.location.hostname, 9004) { msg: JsonElement ->
    when(msg.jsonString("event")) {
      "inited" -> {
        if (MDS.logging) console.log("Connected to Minima.")
        scope.launch {
          blockNumber = MDS.getBlockNumber()
          balances.putAll(MDS.getBalances().associateBy { it.tokenId })
          createDB()
          channels.addAll(getChannels(status = "OPEN"))
          channels.forEach { channel ->
            subscribe(channelKey(channel.my.keys, channel.tokenId), from = channel.updatedAt).onEach { msg ->
              console.log("tx msg", msg)
              val splits = msg.split(";")
              if (splits[0].startsWith("TXN_UPDATE")) {
                channels.first { it.id == channel.id }.update(splits[0].endsWith("_ACK"), updateTx = splits[1], settleTx = splits[2])
              }
            }.onCompletion {
              console.log("completed")
            }.launchIn(scope)
          }
        }
      }
      "NEWBLOCK" -> {
        blockNumber = msg.jsonObject["data"]!!.jsonObject["txpow"]!!.jsonObject["header"]!!.jsonString("block")!!.toInt()
        if (multisigScriptAddress.isNotEmpty()) {
          scope.launch {
            val newBalances = MDS.getBalances(multisigScriptAddress, confirmations = 0)
            if (newBalances.any { it.confirmed > ZERO } && multisigScriptBalances.none { it.confirmed > ZERO }) {
              setChannelOpen(multisigScriptAddress)
            }
            multisigScriptBalances.clear()
            multisigScriptBalances.addAll(newBalances)
          }
        }
        if (eltooScriptAddress.isNotEmpty()) {
          scope.launch {
            eltooScriptCoins.put(eltooScriptAddress, MDS.getCoins(address = eltooScriptAddress))
          }
        }
      }
    }
  }
}

suspend fun newKeys(count: Int): List<String> {
  val command = List(count) { "keys action:new;" }.joinToString("\n")
  return MDS.cmd(command)!!.jsonArray.map { it.jsonObject["response"]!!.jsonString("publickey")!! }
}
