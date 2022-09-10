import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import minima.MDS
import kotlin.js.Date

val firebaseApp = Firebase.initialize(options= FirebaseOptions(
  apiKey= "AIzaSyAxCuQGZTOrHLS-qdaUN2LdEkwHSy3CDpw",
  authDomain= "mini-payments.firebaseapp.com",
  projectId= "mini-payments",
  storageBucket= "mini-payments.appspot.com",
  gcmSenderId= "845857085139",
  applicationId= "1:845857085139:web:17b6f44725a166fee7a626"
))

const val COLLECTION = "transactions"

suspend fun fetch(id: String): String? = (
  Firebase.firestore.collection(COLLECTION).document(id).get()
    .takeIf { it.exists }?.get("tx")
  )

fun subscribe(id: String, from: Long? = null): Flow<String> =
  Firebase.firestore.collection(COLLECTION).document(id).snapshots.mapNotNull { doc ->
    if(doc.exists) doc else null
  }.filter{
    from == null || from <= (it.get("timestamp") as? Double ?: 0.0)
  }.mapNotNull {
    it.get("tx")
  }

suspend fun publish(id: String, content: String) {
  Firebase.firestore.collection(COLLECTION).document(id).set(mapOf("tx" to content, "timestamp" to Date.now()))
}

suspend fun createDB() {
  MDS.sql(//"""DROP TABLE IF EXISTS channel;
    """CREATE TABLE IF NOT EXISTS channel(
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR,
    my_balance DECIMAL(20,10),
    other_balance DECIMAL(20,10),
    my_address VARCHAR,
    other_address VARCHAR,
    my_trigger_key VARCHAR,
    my_update_key VARCHAR,
    my_settle_key VARCHAR,
    other_trigger_key VARCHAR,
    other_update_key VARCHAR,
    other_settle_key VARCHAR,
    sequence_number INT,
    time_lock INT,
    trigger_tx VARCHAR,
    update_tx VARCHAR,
    settle_tx VARCHAR,
    multisig_address VARCHAR,
    eltoo_address VARCHAR
  );""".trimMargin())
}

suspend fun getChannels(status: String? = null): List<ChannelState> {
  val sql = MDS.sql("SELECT * FROM channel${status?.let { " WHERE status = '$it'" } ?: ""} ORDER BY id DESC;")
  val rows = sql.rows as Array<dynamic>
  
  return rows.map { row ->
    ChannelState(
      id = (row.ID as String).toInt(),
      sequenceNumber = (row.SEQUENCE_NUMBER as String).toInt(),
      status = row.STATUS as String,
      myBalance = (row.MY_BALANCE as String).toBigDecimal(),
      counterPartyBalance = (row.OTHER_BALANCE as String).toBigDecimal(),
      myAddress = row.MY_ADDRESS as String,
      myTriggerKey = row.MY_TRIGGER_KEY as String,
      myUpdateKey = row.MY_UPDATE_KEY as String,
      mySettleKey = row.MY_SETTLE_KEY as String,
      counterPartyAddress = row.OTHER_ADDRESS as String,
      counterPartyTriggerKey = row.OTHER_TRIGGER_KEY as String,
      counterPartyUpdateKey = row.OTHER_UPDATE_KEY as String,
      counterPartySettleKey = row.OTHER_SETTLE_KEY as String,
      triggerTx = row.TRIGGER_TX as String,
      updateTx = row.UPDATE_TX as String,
      settlementTx = row.SETTLE_TX as String,
      timeLock = (row.TIME_LOCK as String).toInt(),
      eltooAddress = row.ELTOO_ADDRESS as String,
      updatedAt = Date.parse(row.UPDATED_AT as String).toLong()
    )
  }
}

suspend fun updateChannelStatus(channel: ChannelState, status: String): ChannelState {
  MDS.sql("""UPDATE channel SET
    status = '$status',
    updated_at = NOW()
    WHERE id = ${channel.id};
  """)
  return channel.copy(status = status, updatedAt = Date.now().toLong())
}

suspend fun setChannelOpen(multisigAddress: String) {
  MDS.sql("""UPDATE channel SET
    updated_at = NOW(),
    status = 'OPEN'
    WHERE multisig_address = '$multisigAddress'
    AND status = 'OFFERED';
  """)
}

suspend fun updateChannel(
  channel: ChannelState,
  channelBalance: Pair<BigDecimal, BigDecimal>,
  sequenceNumber: Int,
  updateTx: String,
  settlementTx: String
): ChannelState {
  MDS.sql("""UPDATE channel SET
    my_balance = ${channelBalance.first.toPlainString()},
    other_balance = ${channelBalance.second.toPlainString()},
    sequence_number = $sequenceNumber,
    update_tx = '$updateTx',
    settle_tx = '$settlementTx',
    updated_at = NOW()
    WHERE id = ${channel.id};
  """)
  return channel.copy(
    myBalance = channelBalance.first,
    counterPartyBalance = channelBalance.second,
    sequenceNumber = sequenceNumber,
    updateTx = updateTx,
    settlementTx = settlementTx,
    updatedAt = Date.now().toLong()
  )
}