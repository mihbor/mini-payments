package minima
//
//import kotlinx.browser.window
//import org.w3c.dom.WebSocket
//import org.w3c.dom.WindowOrWorkerGlobalScope
//import org.w3c.fetch.Headers
//import org.w3c.fetch.RequestInit
//import kotlin.coroutines.suspendCoroutine
//import kotlin.js.Date
//
//@Suppress("NOTHING_TO_INLINE")
//inline fun <T : Any> jsObject(): T = js("({})")
//inline fun <T : Any> jsObject(builder: T.() -> Unit): T = jsObject<T>().apply(builder)
//
//external fun decodeURIComponent(encodedURI: String): String
//external fun encodeURIComponent(string: String): String
//
//typealias Callback = ((dynamic) -> Unit)?
//
///**
// * The MAIN Minima Callback function
// */
//var MINIMA_MAIN_CALLBACK: Callback = null
//
///**
// * The MiniDAPP interfce Callback function
// */
//var MINIMA_MINIDAPP_CALLBACK: Callback = null
//
///**
// * The Web Socket Host for PUSH messages
// */
//var MINIMA_WEBSOCKET : WebSocket? = null
//
///**
// * GET or POST request parameters
// */
//var MINIMA_PARAMS: dynamic = null
//
///**
// * NET socket port and functions
// */
//var MINIMA_SERVER_LISTEN: dynamic = null
//var MINIMA_USER_LISTEN: dynamic   = null
//
//external val self: WindowOrWorkerGlobalScope
//
///**
// * Main MINIMA Object for all interaction
// */
//object Minima {
//  /**
//   * Current Minima Block Height
//   */
//  var block = 0
//
//  /**
//   * The Full TxPoW Top Block
//   */
//  var txpow: dynamic = null
//
//  /**
//   * Current Balance of this User
//   */
//  var balance: dynamic = null
//
//  /**
//   * The MiniDAPP ID
//   */
//  var minidappid: String? = null
//
//  //Web Host for the MinDAPP System
//  var webhost = "http://127.0.0.1:9004"
//
//  //RPC Host for Minima
//  var rpchost = "http://127.0.0.1:9002"
//
//  //Web Socket Host for Minima
//  var wshost = "ws://127.0.0.1:9003"
//
//  //Show RPC commands
//  var logging = false
//
//  //Are we in DEBUG mode - if so don't touch the host settings...
//  var debug = false
//
//  suspend fun init() = suspendCoroutine<Unit> { cont ->
//    init {
//      val msg = it
//      if (msg.event == "connected") {
//        if (debug) console.log("Connected to Minima.")
//        cont.resumeWith(Result.success(Unit))
//      }
//    }
//  }
//
//  fun init(callback: ((dynamic) -> Unit)?) {
//
//    log("Initialising...")
//
//    val inWindow = try {
//      window != undefined
//    } catch (t: Throwable) {
//      false
//    }
//
//    fun getMinidappId(): String {
//      if (inWindow) {
//        //Calculate MiniDAPP ID given HREF location
//        val startId = window.location.href.indexOf("/minidapps")
//        val endId = window.location.href.indexOf("/", startId + 11)
//
//        if (startId != -1 && endId != -1) {
//          return window.location.href.substring(startId + 11, endId)
//        } else {
//          log("Not running on /minidapps URL... MiniDAPP ID remains unchanged : $minidappid")
//        }
//      } else {
//        log("Not running in window. Service-worker?")
//      }
//      return "0x00"
//    }
//
//    if(minidappid == null) {
//      minidappid = getMinidappId()
//    }
//    log("MiniDAPP ID set : $minidappid")
//
//    //Store the callback
//    if (callback != null) {
//      MINIMA_MAIN_CALLBACK = callback
//    } else {
//      log("No Main Minima Callback specified..")
//    }
//
//    //Are we running via a server - otherwise leave as is
//    if (!debug && inWindow) {
//      if(window.location.protocol.startsWith("http")) {
//        //The Port determives the WebSocket and RPC port...
//        webhost = "http://${window.location.hostname}:${window.location.port.toInt()}"
//        rpchost = "http://${window.location.hostname}:${window.location.port.toInt() - 2}"
//        wshost  = "ws://${window.location.hostname}:${window.location.port.toInt() - 1}"
//      }
//    }
//
//    log("WEBHOST : $webhost")
//    log("RPCHOST : $rpchost")
//    log("WSHOST  : $wshost")
//
//    val paramString = "$webhost/params"
//    httpGetAsync<dynamic>(paramString) { jsonResp ->
//      MINIMA_PARAMS = jsonResp
//    }
//
//    //Do the first call...
//    cmd("topblock;balance") { json: Array<dynamic> ->
//      if (json[0].status != null) {
//        block  = (json[0].response.txpow.header.block as String).toInt(10)
//        txpow  = json[0].response.txpow
//
//        if (json[1].status != null) {
//          balance = json[1].response.balance
//        }
//      } else {
//        log("Initial CMD calls failed.. Minima still starting up / busy ?")
//      }
//
//      //Start Listening for messages...
//      MinimaWebSocketListener()
//    }
//  }
//  /**
//   * Log some data with a timestamp in a consistent manner to the console
//   */
//  fun log(output: Any?) {
//    console.log("Minima @ ${Date().toLocaleString()} : $output")
//  }
//
//  /**
//   * Runs a function on the Minima Command Line
//   */
//  fun cmd(miniFunc: String, callback: Callback) {
//    MinimaRPC("cmd", miniFunc, callback)
//  }
//
//  suspend fun cmd(miniFunc: String) = suspendCoroutine<dynamic> { cont ->
//    cmd(miniFunc) { response ->
//      cont.resumeWith(Result.success(response))
//    }
//  }
//
//  /**
//   * Run SQL
//   */
//  fun sql(query: String, callback: Callback = null) {
//    MinimaRPC("sql", query, callback)
//  }
//
//  /**
//   * NETWORK Functions
//   */
//  object net {
//    //SERVER FUNCTIONS
//    fun onInbound(port: Int, onReceiveCallback: Callback = null) {
//      MINIMA_SERVER_LISTEN.push("""{ "port": $port, "callback": $onReceiveCallback }""")
//    }
//
//    fun start(port: Int) {
//      MinimaRPC("net", "listen " + port, null)
//    }
//
//    fun stop(port: Int) {
//      MinimaRPC("net", "stop " + port, null)
//    }
//
//    fun broadcast(port: Int, text: String) {
//      MinimaRPC("net", "broadcast $port $text", null)
//    }
//
//    //USER FUNCTIONS
//    fun onOutbound(port: Int, onReceiveCallback: Callback = null) {
//      MINIMA_USER_LISTEN.push("""{ "port": $port, "callback": $onReceiveCallback }""")
//    }
//
//    fun connect(port: Int) {
//      MinimaRPC("net", "connect $port", null)
//    }
//
//    fun disconnect(uid: String) {
//      MinimaRPC("net", "disconnect $uid", null)
//    }
//
//    fun send(uid: String, text: String) {
//      MinimaRPC("net", "send $uid $text", null)
//    }
//
//    //Resend all the connection information
//    fun info() {
//      MinimaRPC("net", "info", null)
//    }
//
//    //Receive all info in the callback
//    fun stats(callback: Callback = null) {
//      MinimaRPC("net", "stats", callback)
//    }
//
//    //GET an URL
//    fun GET(url: String, callback: Callback = null) {
//      MinimaRPC("net", "get "+url, callback)
//    }
//
//    //POST params to an URL
//    fun POST(url: String, params: Any, callback: Callback = null) {
//      MinimaRPC("net", "post $url $params", callback)
//    }
//
//  }
//
//  /**
//   * FILE Functions - no spaces allowed in filenames
//   */
//  object file {
//
//    //Save & Load Text to a file
//    fun save(text: String, file: String, callback: Callback = null) {
//      MinimaRPC("file", "save $file $text", callback)
//    }
//
//    fun load(file: String, callback: Callback = null) {
//      MinimaRPC("file", "load $file", callback)
//    }
//
//    //Save and Load as HEX.. Strings with 0x..
//    fun saveHEX(hextext: String, file: String, callback: Callback = null) {
//      MinimaRPC("file", "savehex $file $hextext", callback)
//    }
//
//    fun loadHEX(file: String, callback: Callback = null) {
//      MinimaRPC("file"," loadhex $file", callback)
//    }
//
//    //Copy file..
//    fun copy(file: String, newFile: String, callback: Callback = null) {
//      MinimaRPC("file", "copy $file $newFile", callback)
//    }
//
//    //Rename a file in your folder
//    fun move(file: String, newFile: String, callback: Callback = null) {
//      MinimaRPC("file", "move $file $newFile", callback)
//    }
//
//    //List the files in a directory
//    fun list(file: String, callback: Callback = null) {
//      MinimaRPC("file", "list $file", callback)
//    }
//
//    //Delete a File
//    fun delete(file: String, callback: Callback = null) {
//      MinimaRPC("file", "delete $file", callback)
//    }
//  }
//  /**
//   * Form GET / POST parameters..
//   */
//  object form {
//
//    //BOTH POST and GET parameters.. and any files are uploaded to /upload folder
//    //must set POST form to multipart/form-data to work..
//    fun params(paramName: String) = MINIMA_PARAMS?.get(paramName)
//
//    //Return the GET parameter by scraping the location..
//    fun getParams(parameterName: String): String? {
//      val params = window.location.search.substring(1).split("&")
//      return params
//        .map { it.split("=") }
//        .findLast { it[0] == parameterName }
//        ?.let{ decodeURIComponent(it[1]) }
//    }
//  }
//
//  /**
//   * Intra MiniDAPP communication
//   */
//  object minidapps {
//    //List the currently installed minidapps
//    fun list(callback: Callback) {
//      cmd("minidapps list", callback)
//    }
//
//    //Function to call when an Intra-MiniDAPP message is received
//    fun listen(onReceiveCallback: Callback) {
//      MINIMA_MINIDAPP_CALLBACK = onReceiveCallback
//    }
//
//    //Send a message to a specific minidapp
//    fun send(minidappId: String, message: String, callback: Callback) {
//      cmd("""minidapps post:$minidappId"$message"""", callback)
//    }
//
//    //The replyid is in the original message
//    fun reply(replyId: String, message: String) {
//      //Reply to a POST message...
//      val replyMsg = """{ "type": "reply", "message": $message, "replyid" : $replyId }"""
//      MINIMA_WEBSOCKET?.send(replyMsg)
//    }
//
//  }
//  /**
//   * UTILITY FUNCTIONS
//   */
//  object util {
//    //Get the Balance string for a Tokenid...
//    fun getBalance(tokenId: String) : String {
//      for (balloop in balance) {
//        if (balloop.tokenid == tokenId){
//          val bal     = balloop.confirmed
//          val balsend = balloop.sendable
//          val balun   = balloop.unconfirmed
//          val mempool = balloop.mempool
//
//          //Is there unconfirmed money coming..
//          return if (balun !== "0" || mempool !== "0" || balsend !== bal) {
//            "$balsend ($bal) / $balun / $mempool"
//          } else {
//            "$bal"
//          }
//        }
//      }
//
//      //Not found...
//      return "0"
//    }
//
//    fun checkAllResponses(responses: Array<dynamic>): Boolean {
//      responses.forEachIndexed { index, response ->
//        if (response.status != true) {
//          window.alert("${response.message}\n\nERROR @ ${response.minifunc}")
//          log("ERROR in Multi-Command [$index] ${JSON.stringify(response, null, 2)}")
//          return false
//        }
//      }
//
//      return true
//    }
//
//    fun getStateVariable(stateList: Array<dynamic>, port: Int): dynamic {
//      for (state in stateList) {
//        if (state.port == port) {
//          return state.data
//        }
//      }
//
//      //Not found
//      return null
//    }
//  }
//}
//
///**
// * POST the RPC call - can be cmd/sql/file/net
// */
//fun MinimaRPC(type: String, data: String, callback: Callback = null) {
//  //And now fire off a call saving it
//  httpPostAsync(Minima.rpchost+"/"+type+"/"+Minima.minidappid, encodeURIComponent(data), callback)
//}
//
///**
// * Post a message to the Minima Event Listeners
// */
//fun MinimaPostMessage(event: String, info: dynamic) {
////  console.log("MinimaPostMessage(event: $event, info: ${JSON.stringify(info)})")
//  val data = jsObject<dynamic>{ this.event = event; this.info = info }
//
//  MINIMA_MAIN_CALLBACK?.let{ it(data) }
//}
//
///**
// * Start listening for PUSH messages
// */
//fun MinimaWebSocketListener() {
//  Minima.log("Starting WebSocket Listener @ ${Minima.wshost}")
//
//  //Check connected
//  MINIMA_WEBSOCKET?.close()
//
//  //Open up a websocket to the main MINIMA proxy...
//  MINIMA_WEBSOCKET = WebSocket(Minima.wshost)
//
//  MINIMA_WEBSOCKET?.onopen = {
//    //Connected
//    Minima.log("Minima WS Listener Connection opened..")
//
//    //Now set the MiniDAPPID
//    val uid = """{ "type":"minidappid", "minidappid": "${Minima.minidappid}" }"""
//
//    //Send your name... set automagically but can be hard set when debugging
//    MINIMA_WEBSOCKET?.send(uid)
//
//    //Send a message
//    MinimaPostMessage("connected", "\"success\"")
//  }
//
//  MINIMA_WEBSOCKET?.onmessage = { evt:dynamic ->
//    //Convert to JSON
//    val jmsg = JSON.parse<dynamic>(evt.data)
//
//    if (jmsg.event == "newblock") {
//      //Set the new status
//      Minima.block = (jmsg.txpow.header.block as String).toInt(10)
//      Minima.txpow = jmsg.txpow
//
//      val info = jsObject<dynamic>{ txpow = jmsg.txpow }
//
//      MinimaPostMessage("newblock", info)
//
//    } else if(jmsg.event == "newtransaction") {
//      val info = jsObject<dynamic>{ txpow = jmsg.txpow; relevant = jmsg.relevant }
//
//      MinimaPostMessage("newtransaction", info)
//
//    } else if(jmsg.event == "newtxpow") {
//      val info = jsObject<dynamic> { txpow = jmsg.txpow }
//
//      MinimaPostMessage("newtxpow", info)
//
//    } else if(jmsg.event == "newbalance") {
//      //Set the New Balance
//      Minima.balance = jmsg.balance
//
//      val info = jsObject<dynamic> { balance = jmsg.balance }
//
//      MinimaPostMessage("newbalance", info)
//
//    } else if(jmsg.event == "network") {
//      //What type of message is it...
//      if (jmsg.details.action == "server_start"
//        || jmsg.details.action == "server_stop"
//        || jmsg.details.action == "server_error") {
//
//        sendCallback(MINIMA_SERVER_LISTEN, jmsg.details.port, jmsg.details)
//
//      } else if(jmsg.details.action == "client_new"
//        || jmsg.details.action == "client_shut"
//        || jmsg.details.action == "message") {
//
//        if (!jmsg.details.outbound) {
//          sendCallback(MINIMA_SERVER_LISTEN, jmsg.details.port, jmsg.details)
//        } else {
//          sendCallback(MINIMA_USER_LISTEN, jmsg.details.hostport, jmsg.details)
//        }
//      } else if(jmsg.details.action == "post") {
//        MINIMA_MINIDAPP_CALLBACK?.let{ it(jmsg.details) }
//          ?: Minima.minidapps.reply(jmsg.details.replyid, "ERROR - no minidapp interface found")
//
//      } else {
//        Minima.log("UNKNOWN NETWORK EVENT : ${evt.data}")
//      }
//
//    } else if(jmsg.event == "txpowstart") {
//      val info = jsObject<dynamic> { transaction = jmsg.transaction }
//      MinimaPostMessage("miningstart", info)
//
//    } else if(jmsg.event == "txpowend") {
//      val info = jsObject<dynamic> { transaction = jmsg.transaction }
//      MinimaPostMessage("miningstop", info)
//    }
//  }
//
//  MINIMA_WEBSOCKET?.onclose = {
//    Minima.log("Minima WS Listener closed... reconnect attempt in 10 seconds")
//
//    //Start her up in a minute...
//    window.setTimeout({ MinimaWebSocketListener() }, 10000)
//  }
//
//  MINIMA_WEBSOCKET?.onerror = { error: dynamic ->
//    //var err = JSON.stringify(error)
//    val err = JSON.stringify(error, arrayOf("message", "arguments", "type", "name", "data"))
//
//    // websocket is closed.
//    Minima.log("Minima WS Listener Error ... $err")
//  }
//}
//
//fun sendCallback(list: List<dynamic>, port: Int, msg: String) {
//  list.find { it.port == port }?.callback(msg)
//}
//
///**
// * Utility function for GET request
// *
// * @param url
// * @param callback
// * @param params
// * @returns
// */
//fun httpPostAsync(url: String, params: Any?, callback: Callback = null) {
//
//  val headers = Headers()
//  headers.set("Content-Type", "application/x-www-form-urlencoded")
//  self.fetch(url, RequestInit(
//    "POST",
//    headers,
//    params,
//  )).then { response ->
//    if(response.ok) {
//      if (Minima.logging) {
//        Minima.log("RPC:$url\nPARAMS:$params\nRESPONSE:${response.body}")
//      }
//
//      if (callback != null) {
//        response.json()
//          .then(callback)
//      }
//    }
//  }
//
//}
//
///**
// * Utility function for GET request
// *
// * @param url
// * @param callback
// * @returns
// */
//fun <T>httpGetAsync(url: String, logEnabled: Boolean = false, callback: ((T) -> Unit)?) {
//  self.fetch(url, RequestInit(
//    "GET"
//  )).then { response ->
//    if(response.ok) {
//      if(Minima.logging && logEnabled) {
//        val logString = JSON.stringify(response.body, null, 2).replace("\\n","\n")
//        Minima.log("$url\n$logString")
//      }
//
//      if (callback != null) {
//        callback(response.body)
//      }
//    }
//  }
//}
