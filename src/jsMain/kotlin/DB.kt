import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize

val firebaseApp = Firebase.initialize(options= FirebaseOptions(
  
  apiKey= "AIzaSyAxCuQGZTOrHLS-qdaUN2LdEkwHSy3CDpw",
  authDomain= "mini-payments.firebaseapp.com",
  projectId= "mini-payments",
  storageBucket= "mini-payments.appspot.com",
  gcmSenderId= "845857085139",
  applicationId= "1:845857085139:web:17b6f44725a166fee7a626"
))

const val COLLECTION = "transactions"

suspend fun fetch(id: String): String? =
  (Firebase.firestore.collection(COLLECTION).document(id).get().data() as Map<String, String>)["tx"]

suspend fun store(id: String, content: String) {
  Firebase.firestore.collection(COLLECTION).document(id).set(mapOf("tx" to content))
}