import com.ionspin.kotlin.bignum.decimal.BigDecimal

data class ChannelState(
  val id: Int,
  val sequenceNumber: Int = 0,
  val status: String,
  val tokenId: String = "0x00",
  val myBalance: BigDecimal,
  val counterPartyBalance: BigDecimal,
  val myAddress: String,
  val myTriggerKey: String,
  val myUpdateKey: String,
  val mySettleKey: String,
  val counterPartyAddress: String = "",
  val counterPartyTriggerKey: String,
  val counterPartyUpdateKey: String,
  val counterPartySettleKey: String,
  val triggerTx: String,
  val updateTx: String = "",
  val settlementTx: String,
  val timeLock: Int,
  val eltooAddress: String,
  val updatedAt: Long
)
