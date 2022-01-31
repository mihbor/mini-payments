import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.json.decodeFromDynamic
import minima.Balance
import minima.Minima
import kotlin.random.Random

val balances = mutableStateListOf<Balance>()

fun newTxId() = Random.nextInt(1_000_000_000)

fun init() {
  Minima.debug = true
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

suspend fun post(toAddress: String, amount: Double, tokenId: String) {
  val txnId = newTxId()
  
  val txncreator = "txncreate $txnId;" +
    "txnauto $txnId $amount $toAddress $tokenId;" +
    "txnpost $txnId;" +
    "txndelete $txnId;"
  
  val result = Minima.cmd(txncreator)
}