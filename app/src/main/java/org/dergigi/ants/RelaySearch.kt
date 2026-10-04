package org.dergigi.ants

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.*
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

val defaultSearchRelays = listOf("wss://search.nos.today", "wss://relay.ditto.pub", "wss://antiprimal.net", "wss://nostr.me/relay", "wss://relay.crostr.com", "wss://search.brainstorm.world")
val generalRelays = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net")
sealed interface RelayUpdate {
    data class Event(val event: Nip01Event, val relay: String) : RelayUpdate
    data class Status(val relay: String, val text: String) : RelayUpdate
}

class RelaySearch {
    val http = OkHttpClient.Builder().connectTimeout(7, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    fun search(branches: List<SearchBranch>, relays: List<String>, duration: Long = 14000) =
        searchRoutes(relays.distinct().associateWith { branches }, duration)

    fun searchRoutes(routes: Map<String, List<SearchBranch>>, duration: Long = 14000) = callbackFlow {
        val pending = ConcurrentHashMap.newKeySet<String>().apply { addAll(routes.keys) }
        val seen = ConcurrentHashMap.newKeySet<String>()
        val sockets = mutableListOf<WebSocket>()
        val admission = Any()
        val completion = Any()
        var retainedBytes = 0L
        fun finish(url: String, status: String) {
            synchronized(completion) {
                if (!pending.remove(url)) return
                // Preserve terminal statuses even when the bounded queue is full.
                trySendBlocking(RelayUpdate.Status(url, status))
                if (pending.isEmpty()) close()
            }
        }
        routes.forEach { (url, branches) ->
            val subscriptions = branches.mapIndexed { index, branch -> "ants-$index" to branch }.toMap()
            val active = ConcurrentHashMap.newKeySet<String>().apply { addAll(subscriptions.keys) }
            fun completeSubscription(id: String, status: String) {
                if (active.remove(id) && active.isEmpty()) finish(url, status)
            }
            trySend(RelayUpdate.Status(url, "Connecting"))
            sockets += http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    trySend(RelayUpdate.Status(url, "Searching"))
                    subscriptions.forEach { (id, branch) ->
                        webSocket.send(JSONArray().put("REQ").put(id).put(branch.filter).toString())
                    }
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (url !in pending || text.length > 1_000_000) return
                    runCatching {
                        val message = JSONArray(text)
                        when (message.optString(0)) {
                            "EVENT" -> if (message.optString(1) in active && seen.size < 500) {
                                val branch = subscriptions[message.optString(1)] ?: return
                                val event = Nip01Event.parse(message.getJSONObject(2)) ?: return
                                if (event.id !in seen && branch.accepts(event) && event.verify()) {
                                    val admitted = synchronized(admission) {
                                        when {
                                            event.id in seen || seen.size >= 500 -> 0
                                            retainedBytes + event.retainedBytes > RESULT_MEMORY_BUDGET -> -1
                                            else -> { seen.add(event.id); retainedBytes += event.retainedBytes; 1 }
                                        }
                                    }
                                    if (admitted > 0) trySendBlocking(RelayUpdate.Event(event, url))
                                    else if (admitted < 0) {
                                        active.forEach { webSocket.send(JSONArray().put("CLOSE").put(it).toString()) }
                                        finish(url, RESULT_MEMORY_LIMIT)
                                    }
                                }
                            }
                            "EOSE" -> if (message.optString(1) in active) {
                                val id = message.getString(1)
                                webSocket.send(JSONArray().put("CLOSE").put(id).toString())
                                completeSubscription(id, "Complete")
                            }
                            "CLOSED" -> completeSubscription(message.optString(1), message.optString(2).take(120).ifBlank { "Closed" })
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
        if (routes.isEmpty()) close()
        awaitClose { timeout.cancel(); sockets.forEach { it.cancel() } }
    }.buffer(8).flowOn(Dispatchers.IO)
}
