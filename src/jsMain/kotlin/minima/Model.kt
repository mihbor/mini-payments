package minima

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Balance(
  val tokenid: String,
  val token: String,
  val total: String,
  val decimals: String,
  @Contextual
  val confirmed: BigDecimal,
  @Contextual
  val unconfirmed: BigDecimal,
  val mempool: String,
  @Contextual
  val sendable: BigDecimal
)

@Serializable
data class CoinSimple(
  val key: String,
  @Contextual
  val tokenamount: BigDecimal,
  val coin: Coin
)

@Serializable
data class Coin(
  val address: String,
  val amount: String,
  val coinid: String,
  val floating: Boolean,
  val mxaddress: String,
  val storestate: Boolean,
  val tokenid: String
)

@Serializable
data class Token(
  val tokenid: String,
  val token: String,
  val total: String,
  val decimals: String,
  val description: String? = null,
  val icon: String? = null,
  val proof: String? = null,
  val script: String? = null,
  val coinid: String? = null,
  val totalamount: String? = null,
  val scale: String? = null
)
