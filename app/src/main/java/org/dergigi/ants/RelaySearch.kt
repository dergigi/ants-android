package org.dergigi.ants

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.*
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

val defaultSearchRelays = listOf("wss://search.nos.today", "wss://relay.ditto.pub", "wss://antiprimal.net", "wss://nostr.me/relay", "wss://relay.crostr.com")
val generalRelays = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
sealed interface RelayUpdate {
    data class Event(val event: Nip01Event, val relay: String) : RelayUpdate
    data class Status(val relay: String, val text: String) : RelayUpdate
}

class RelaySearch {
    val http = OkHttpClient.Builder().connectTimeout(7, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    fun search(branches: List<SearchBranch>, relays: List<String>, duration: Long = 14000) = callbackFlow {
        val pending = ConcurrentHashMap.newKeySet<String>().apply { addAll(relays) }
        val seen = ConcurrentHashMap.newKeySet<String>()
        val sockets = mutableListOf<WebSocket>()
        fun finish(url: String, status: String) {
            trySend(RelayUpdate.Status(url, status))
            pending.remove(url)
            if (pending.isEmpty()) close()
        }
        relays.forEach { url ->
            trySend(RelayUpdate.Status(url, "Connecting"))
            sockets += http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    trySend(RelayUpdate.Status(url, "Searching"))
                    val req = JSONArray().put("REQ").put("ants")
                    branches.forEach { req.put(it.filter) }
                    webSocket.send(req.toString())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.length > 1_000_000) return
                    runCatching {
                        val message = JSONArray(text)
                        when (message.optString(0)) {
                            "EVENT" -> if (message.optString(1) == "ants" && seen.size < 500) {
                                val event = Nip01Event.parse(message.getJSONObject(2)) ?: return
                                if (event.id !in seen && branches.any { it.accepts(event) } && event.verify() && seen.add(event.id)) trySendBlocking(RelayUpdate.Event(event, url))
                            }
                            "EOSE" -> if (message.optString(1) == "ants") { webSocket.send("[\"CLOSE\",\"ants\"]"); finish(url, "Complete") }
                            "CLOSED" -> if (message.optString(1) == "ants") finish(url, message.optString(2).take(120).ifBlank { "Closed" })
                            "AUTH" -> finish(url, "Login required")
                            "NOTICE" -> trySend(RelayUpdate.Status(url, message.optString(1).take(120)))
                        }
                    }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish(url, "Unavailable") }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish(url, "Closed") }
            })
        }
        val timeout = launch { delay(duration); pending.toList().forEach { finish(it, "Timed out") }; close() }
        if (relays.isEmpty()) close()
        awaitClose { timeout.cancel(); sockets.forEach { it.cancel() } }
    }
}
