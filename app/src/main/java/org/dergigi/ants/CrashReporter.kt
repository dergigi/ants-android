package org.dergigi.ants

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Local capture only. A report leaves the device only after the user taps Send. */
internal object CrashReporter {
    private lateinit var reportFile: AtomicFile
    @Volatile private var screen = "Starting"
    @Volatile private var lastHighlight: String? = null
    private val relay by lazy { RelaySearch() }

    fun install(context: Context) {
        reportFile = AtomicFile(File(context.noBackupFilesDir, "crash-report.txt"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = buildString {
                    appendLine("ants crash report")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.GIT_COMMIT}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Time: ${Instant.now()}")
                    appendLine("Thread: ${thread.name.take(100)}")
                    appendLine("Screen: $screen")
                    lastHighlight?.let { appendLine("Last rendered highlight: $it") }
                    appendLine()
                    append(error.stackTraceToString().take(8000))
                }.replace(Regex("(?i)nsec1[a-z0-9]+"), "[redacted secret]")
                val output = reportFile.startWrite()
                try { output.write(report.toByteArray()); reportFile.finishWrite(output) }
                catch (failure: Throwable) { reportFile.failWrite(output); throw failure }
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else { Process.killProcess(Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }

    fun onScreen(value: String) { screen = value }
    fun onHighlight(eventId: String) { lastHighlight = eventId }
    fun pending(): String? = runCatching { reportFile.openRead().bufferedReader().use { it.readText().take(12000) }.takeIf(String::isNotBlank) }.getOrNull()
    fun clear() { runCatching { reportFile.delete() } }

    suspend fun send(report: String, recipient: String): Boolean = withContext(Dispatchers.IO) {
        require(recipient.matches(Regex("[0-9a-f]{64}")))
        val sender = ClientKeypair.generate()
        val wrap = try { Nip17.giftWrap(report, recipient, sender) } finally { sender.privkey.fill(0) }
        val inboxEvents = mutableListOf<Nip01Event>()
        val filter = JSONObject().put("kinds", JSONArray().put(10050)).put("authors", JSONArray().put(recipient)).put("limit", 1)
        relay.search(listOf(SearchBranch(filter)), generalRelays, 5000).collect {
            if (it is RelayUpdate.Event) inboxEvents.add(it.event)
        }
        val urls = (Nip17.parseDmRelays(inboxEvents) + generalRelays).filter { url ->
            runCatching { val uri = java.net.URI(url); uri.scheme == "wss" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null }.getOrDefault(false)
        }.distinct().take(8)
        publish(wrap, urls)
    }

    private suspend fun publish(event: Nip01Event, urls: List<String>): Boolean = withTimeoutOrNull(12000) {
        callbackFlow {
            val pending = ConcurrentHashMap.newKeySet<String>().apply { addAll(urls) }
            val sockets = mutableListOf<WebSocket>()
            fun finish(url: String) { pending.remove(url); if (pending.isEmpty()) close() }
            try {
                for (url in urls) {
                    sockets += relay.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send(JSONArray().put("EVENT").put(JSONObject(event.toJsonString())).toString())
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (text.length > 10000) return
                            val message = runCatching { JSONArray(text) }.getOrNull() ?: return
                            if (message.optString(0) == "OK" && message.optString(1) == event.id) {
                                if (message.optBoolean(2)) trySend(true)
                                finish(url)
                            }
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish(url) }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish(url) }
                    })
                }
                if (urls.isEmpty()) close()
                awaitClose { }
            } finally { sockets.forEach { it.cancel() } }
        }.firstOrNull() == true
    } ?: false
}
