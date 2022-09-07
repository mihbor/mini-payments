package minima

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromDynamic

@Serializable
data class Output(
  val address: String,
  @Contextual
  val amount: BigDecimal,
  val tokenid: String,
  val miniaddress: String = ""
)

@Serializable
data class State(
  val port: Int,
  val data: String
)

typealias Input = Output

suspend fun getBlockNumber(): Int {
  val status = MDS.cmd("status")
  return status.response.chain.block as Int
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun getBalances(address: String? = null): List<Balance> {
  val balance = MDS.cmd("balance ${address?.let{"address:$address "} ?:""}")
  val balances = balance.response as Array<dynamic>
  return balances.map {
    try {
      Balance(
        token = if (it.token == "Minima") null else json.decodeFromDynamic<TokenDescriptor>(it.token),
        tokenid = it.tokenid,
        confirmed = json.decodeFromDynamic(it.confirmed),
        unconfirmed = json.decodeFromDynamic(it.unconfirmed),
        sendable = json.decodeFromDynamic(it.sendable),
        coins = (it.coins as String).toInt()
      )
    } catch (e: Exception) {
      console.log("Exception mapping balance", it, e)
      throw e
    }
  }
}

suspend fun getAddress(): String {
  val getaddress = MDS.cmd("getaddress")
  return getaddress.response.miniaddress
}

suspend fun newAddress(): String {
  val newaddress = MDS.cmd("newaddress")
  return newaddress.response.miniaddress
}

suspend fun newKey(): String {
  val keys = MDS.cmd("keys action:new")
  return keys.response.publickey as String
}

suspend fun deployScript(text: String): String {
  val newscript = MDS.cmd("""newscript script:"$text" trackall:true""")
  return newscript.response.address
}

suspend fun getCoins(tokenId: String? = null, address: String? = null, sendable: Boolean = false): List<Coin> {
  val coinSimple = MDS.cmd("coins ${tokenId?.let{"tokenid:$tokenId "} ?:""} ${address?.let{"address:$address "} ?:""}sendable:$sendable")
  val coins = json.decodeFromDynamic<Array<Coin>>(coinSimple.response)
  return coins.sortedBy { it.amount }
}

suspend fun signTx(txnId: Int, key: String): dynamic {
  val txncreator = "txnsign id:$txnId publickey:$key;"
  val result = MDS.cmd(txncreator)
  console.log("import", result.status)
  return result
}

suspend fun post(txnId: Int): dynamic {
  val txncreator = "txnpost id:$txnId auto:true;"
  val result = MDS.cmd(txncreator)
  return result.response
}

suspend fun exportTx(txnId: Int): String {
  val txncreator = "txnexport id:$txnId;"
  val result = MDS.cmd(txncreator)
  return result.response.data as String
}

suspend fun importTx(txnId: Int, data: String): JsonObject {
  val txncreator = "txncreate id:$txnId;" +
    "txnimport id:$txnId data:$data;"
  val result = MDS.cmd(txncreator) as Array<dynamic>
  val txnimport = result.find{ it.command == "txnimport" }
  console.log("import", txnimport.status)
  return json.decodeFromDynamic(txnimport.response.transaction)
}
