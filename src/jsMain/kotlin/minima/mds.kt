package minima

import com.ionspin.kotlin.bignum.serialization.kotlinx.bigdecimal.bigDecimalHumanReadableSerializerModule
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Date

external fun decodeURIComponent(encodedURI: String): String
external fun encodeURIComponent(string: String): String

@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> jsObject(): T = js("({})")
inline fun <T : Any> jsObject(builder: T.() -> Unit): T = jsObject<T>().apply(builder)

typealias Callback = ((dynamic) -> Unit)?

val json = Json {
  serializersModule = bigDecimalHumanReadableSerializerModule
  ignoreUnknownKeys = true
}

/**
 * The MAIN Minima Callback function
 */
var MDS_MAIN_CALLBACK: Callback = null;

/**
 * Main MINIMA Object for all interaction
 */
object MDS {
  
  //RPC Host for Minima
  var mainhost = ""
  
  //The MiniDAPP UID
  var minidappuid: String? = null
  
  //Is logging RPC enabled
  var logging = false
  
  //When debuggin you can hard set the Host and port
  var DEBUG_HOST: String? = null
  var DEBUG_PORT = -1
  
  //An allowed TEST Minidapp ID for SQL - can be overridden
  var DEBUG_MINIDAPPID = "0x00"
  
  /**
   * Minima Startup - with the callback function used for all Minima messages
   */
  fun init(callback: Callback) {
    //Log a little..
    log("Initialising .")
    
    //Is logging enabled via the URL
    if(form.getParams("MDS_LOGGING") != null){
      logging = true;
    }
    
    //Get the host and port
    var host = window.location.hostname
    var port = 9003//window.location.port.toInt()
    
    if(logging){
      log("Location : "+window.location)
      log("Host     : "+host)
      log("port     : "+port)
    }
    
    //Get their MiniDAPP UID
    minidappuid = form.getParams("uid")
    
    //HARD SET if debug mode - running from a file
    DEBUG_HOST?.let{
      
      log("DEBUG Settings Found..");
      
      host = it
      port = DEBUG_PORT
    }
    
    if(minidappuid == null){
      minidappuid = DEBUG_MINIDAPPID
    }
    
    //Is one specified..
    if(minidappuid == "0x00"){
      log("No MiniDAPP UID specified.. using test value")
    }
    
    if(logging){
      log("MDS UID  : "+minidappuid)
    }
    
    val mainport 	= port+1
    
    log("MDS FILEHOST  : https://$host:$port/")
    
    mainhost 	= "https://$host:$mainport/"
    log("MDS MAINHOST : "+ mainhost)
    
    //Store this for poll messages
    MDS_MAIN_CALLBACK = callback
    
    //Start the Long Poll listener
    PollListener()
    
    MDSPostMessage(js("""{ "event" : "inited" }"""))
  }
  
  /**
   * Log some data with a timestamp in a consistent manner to the console
   */
  fun log(output: String){
    console.log("Minima @ ${Date().toLocaleString()} : $output")
  }
  
  /**
   * Runs a function on the Minima Command Line - same format as MInima
   */
  fun cmd(command: String, callback: Callback = null){
    //Send via POST
    httpPostAsync("${mainhost}cmd?uid=$minidappuid", command, callback)
  }
  
  suspend fun cmd(miniFunc: String) = suspendCoroutine<dynamic> { cont ->
    cmd(miniFunc) { response ->
      cont.resumeWith(Result.success(response))
    }
  }
  
  /**
   * Runs a SQL command on this MiniDAPPs SQL Database
   */
  fun sql(command: String, callback: Callback = null){
    httpPostAsync("${mainhost}sql?uid=$minidappuid", command, callback)
  }
  
  /**
   * Form GET / POST parameters..
   */
  object form{
  
    //Return the GET parameter by scraping the location..
    fun getParams(parameterName: String): String?{
      var result: String? = null
      val items = window.location.search.substring(1).split("&");
      for (item in items) {
        val tmp = item.split("=");
        //console.log("TMP:"+tmp);
        if (tmp[0] == parameterName) result = decodeURIComponent(tmp[1])
      }
      return result
    }
  }
}

/**
 * Post a message to the Minima Event Listeners
 */
fun MDSPostMessage(data: dynamic){
  MDS_MAIN_CALLBACK?.invoke(data)
}

var PollCounter = 0
var PollSeries  = ""

//@Serializable
//data class Msg(
//  val series: String,
//  val counter: Int,
//  val status: Boolean,
//  val message: dynamic? = null,
//  val response: Msg? = null
//)

fun PollListener(){
  
  //The POLL host
  val pollhost = "${MDS.mainhost}poll?uid=${MDS.minidappuid}"
  val polldata = "series=$PollSeries&counter=$PollCounter"
  
  httpPostAsyncPoll(pollhost, polldata) { msg: dynamic ->
    //Are we on the right Series
    if (PollSeries != msg.series) {
    
      //Reset to the right series.
      PollSeries = msg.series
      PollCounter = msg.counter
    
    } else {
    
      //Is there a message ?
      if (msg.status == true && msg.response?.message != null) {
      
        //Get the current counter
        PollCounter = msg.response.counter + 1
      
        MDSPostMessage(msg.response.message)
      }
    }
  
    //And around we go again
    PollListener()
  }
}

/**
 * Utility function for GET request
 *
 * @param theUrl
 * @param callback
 * @param params
 * @returns
 */
fun <T> httpPostAsync(theUrl: String, params: String, callback: ((T) -> Unit)? = null){
  if(MDS.logging){
    MDS.log("POST_RPC:$theUrl PARAMS:$params")
  }
  
  val xmlHttp = XMLHttpRequest()
  xmlHttp.onreadystatechange = {
    if (xmlHttp.readyState == 4.toShort() && xmlHttp.status == 200.toShort()){
      if(MDS.logging){
        MDS.log("RESPONSE:"+xmlHttp.responseText)
      }
      
      //Send it to the callback function
      callback?.invoke(JSON.parse(xmlHttp.responseText))
    }
  }
  xmlHttp.open("POST", theUrl, true) // true for asynchronous
  xmlHttp.overrideMimeType("text/plain; charset=UTF-8")
  //xmlHttp.setRequestHeader('Content-Type', 'application/json')
  xmlHttp.send(encodeURIComponent(params))
  //xmlHttp.send(params)
}

/**
 * Utility function for GET request (UNUSED for now..)
 *
 * @param theUrl
 * @param callback
 * @returns
 */
/*function httpGetAsync(theUrl, callback)
{
    var xmlHttp = new XMLHttpRequest();
    xmlHttp.onreadystatechange = function() {
        if (xmlHttp.readyState == 4 && xmlHttp.status == 200){
        	if(MDS.logging){
				console.log("RPC      : "+theUrl);
				console.log("RESPONSE : "+xmlHttp.responseText);
			}

			//Always a JSON ..
        	var rpcjson = JSON.parse(xmlHttp.responseText);
        	
        	//Send it to the callback function..
        	if(callback){
        		callback(rpcjson);
        	}
        }
    }
	xmlHttp.open("GET", theUrl, true); // true for asynchronous
    xmlHttp.send(null);
}*/

fun <T> httpPostAsyncPoll(theUrl: String, params: String, callback: ((T) -> Unit)){
  if(MDS.logging){
    MDS.log("POST_POLL_RPC:$theUrl PARAMS:$params")
  }
  
  val xmlHttp = XMLHttpRequest()
  xmlHttp.onreadystatechange = {
    if (xmlHttp.readyState == 4.toShort() && xmlHttp.status == 200.toShort()){
      if(MDS.logging){
        MDS.log("RESPONSE:"+xmlHttp.responseText);
      }
      
      callback.invoke(JSON.parse(xmlHttp.responseText))
    }
  }
  xmlHttp.addEventListener("error", {
    MDS.log("Error Polling - reconnect in 10s")
    window.setTimeout({PollListener()}, 10000)
  });
  xmlHttp.open("POST", theUrl, true) // true for asynchronous
  xmlHttp.overrideMimeType("text/plain; charset=UTF-8")
  xmlHttp.send(encodeURIComponent(params))
}