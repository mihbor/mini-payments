import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import minima.MDS

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
    .takeIf { it.exists }?.data() as Map<String, String>?
  )?.let{ it["tx"] }

fun subscribe(id: String): Flow<String> =
  Firebase.firestore.collection(COLLECTION).document(id).snapshots.mapNotNull { doc ->
      ((if(doc.exists) doc else null)?.data() as Map<String, String>?)
        ?.let { data -> data["tx"] }
  }

suspend fun publish(id: String, content: String) {
  Firebase.firestore.collection(COLLECTION).document(id).set(mapOf("tx" to content))
}

suspend fun createDB() {
  MDS.sql(///"""DROP TABLE IF EXISTS channel;
    """CREATE TABLE IF NOT EXISTS channel(
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR,
    my_balance DECIMAL(20,10),
    other_balance DECIMAL(20,10),
    my_trigger_key VARCHAR,
    my_update_key VARCHAR,
    my_settle_key VARCHAR,
    other_trigger_key VARCHAR,
    other_update_key VARCHAR,
    other_settle_key VARCHAR,
    sequence_number INT,
    trigger_tx VARCHAR,
    update_tx VARCHAR,
    settle_tx VARCHAR
  );""".trimMargin())
}

suspend fun getChannels(): List<ChannelState> {
  val sql = MDS.sql("SELECT id, status, sequence_number FROM channel;")
  val rows = sql.rows as Array<dynamic>
  
  return rows.map { row ->
    ChannelState(
      id = (row.ID as String).toInt(),
      sequenceNumber = (row.SEQUENCE_NUMBER as String).toInt(),
      status = row.STATUS as String,
    )
  }
}