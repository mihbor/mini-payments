import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.json.decodeFromDynamic
import minima.Balance
import minima.Minima
import kotlin.random.Random

fun script(blockDiff: Int, updateSig1: String, updateSig2: String, settleSig1: String, settleSig2: String) =
  "RETURN MULTISGI(2 $updateSig1 $updateSig2) OR ( @BLKDIFF GTE $blockDiff AND MULTISIG(2 $settleSig1 $settleSig2) )"

val balances = mutableStateListOf<Balance>()

fun newTxId() = Random.nextInt(1_000_000_000)

fun init() {
  Minima.debug = true
  Minima.logging = true
  Minima.init { msg ->
    when (msg.event) {
      "connected" -> {
        if (Minima.debug) console.log("Connected to Minima.")
        balances.addAll(getBalances())
      }
    }
  }
}

fun getBalances() = json.decodeFromDynamic<Array<Balance>>(Minima.balance)

suspend fun newAddress(): String {
  val newaddress = Minima.cmd("newaddress")
  return newaddress.response.address.miniaddress
}

suspend fun newKey(): String {
  val keys = Minima.cmd("keys new")
  return keys.response.key.publickey as String
}

suspend fun deployScript(text: String): String {
  val newscript = Minima.cmd("""newscript "$text"""")
  return newscript.response.address.hexaddress
}

suspend fun post(toAddress: String, amount: Double, tokenId: String) {
  val txnId = newTxId()
  
  val txncreator = "txncreate $txnId;" +
    "txnauto $txnId $amount $toAddress $tokenId;" +
    "txnpost $txnId;" +
    "txndelete $txnId;"
  
  val result = Minima.cmd(txncreator)
}

suspend fun exportTx(toAddress: String, amount: Double, tokenId: String): String {
  val txnId = newTxId()
  
  val txncreator = "txncreate $txnId;" +
    "txnauto $txnId $amount $toAddress $tokenId;" +
    "txnexport $txnId;"
  
  val result = Minima.cmd(txncreator) as Array<dynamic>
  val txnexport = result.find{it.minifunc == "txnexport $txnId"}
  console.log("export", txnexport.response.transaction)
  return txnexport.response.transaction as String
}

suspend fun importTx(tx: String) {
  val txnId = newTxId()
  
  val txncreator = "txncreate $txnId;" +
    "txnimport $txnId $tx"
  val result = Minima.cmd(txncreator) as Array<dynamic>
  val txnimport = result.find{(it.minifunc as String).startsWith("txnimport $txnId")}
  console.log("import", txnimport.message)
}