package org.dergigi.ants

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

internal data class AppSettings(val richPreviews: Boolean = true, val sync: String = "Local settings")

/** NIP-78, following Boris's public, app-specific settings convention. */
internal class AppSettingsStore(
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    private val relay: RelaySearch,
    private val sign: suspend (Nip01Event) -> Nip01Event?,
) {
    private val mutable = MutableStateFlow(AppSettings())
    val state = mutable.asStateFlow()
    private var account: String? = null
    private var job: Job? = null
    private val identifier = "org.dergigi.ants.user-settings"
    private fun key() = "app-settings:${account ?: "guest"}"
    private fun document() = runCatching { JSONObject(preferences.getString(key(), "{}")!!) }.getOrDefault(JSONObject())
    fun connect(pubkey: String?) {
        job?.cancel()
        account = pubkey
        mutable.value = AppSettings(document().optBoolean("richPreviews", true))
        if (pubkey != null) sync()
    }
    fun setRichPreviews(enabled: Boolean) {
        if (enabled == state.value.richPreviews) return
        val doc = document().put("richPreviews", enabled)
        preferences.edit().putString(key(), doc.toString()).putBoolean(key() + ":dirty", account != null).apply()
        mutable.value = AppSettings(enabled, if (account == null) "Local settings" else "Sync pending")
        if (account != null) sync()
    }
    fun refresh() { if (job?.isActive != true) sync() }
    fun sync() {
        val author = account ?: return
        job?.cancel()
        val storageKey = key()
        job = scope.launch {
            mutable.update { it.copy(sync = "Syncing…") }
            try {
                val routes = generalRelays.toMutableList()
                val discovery = JSONObject().put("kinds", JSONArray().put(10002)).put("authors", JSONArray().put(author)).put("limit", 1)
                var relayList: Nip01Event? = null
                relay.search(listOf(SearchBranch(discovery)), generalRelays, 4000).collect { update ->
                    if (update is RelayUpdate.Event && update.event.createdAt > (relayList?.createdAt ?: 0)) relayList = update.event
                }
                val advertised = relayList?.let(::advertisedRelays)
                routes.addAll(advertised?.read.orEmpty())
                routes.addAll(advertised?.write.orEmpty())
                val publishRoutes = (generalRelays + advertised?.write.orEmpty()).distinct()
                val filter = JSONObject().put("kinds", JSONArray().put(30078)).put("authors", JSONArray().put(author))
                    .put("#d", JSONArray().put(identifier)).put("limit", 1)
                var latest: Nip01Event? = null
                var read = false
                relay.search(listOf(SearchBranch(filter)), routes.distinct(), 6000).collect { update ->
                    if (update is RelayUpdate.Status && update.text == "Complete") read = true
                    if (update is RelayUpdate.Event && update.event.tagValue("d") == identifier) {
                        val old = latest
                        if (old == null || update.event.createdAt > old.createdAt || update.event.createdAt == old.createdAt && update.event.id < old.id) latest = update.event
                    }
                }
                ensureActive()
                if (account != author) return@launch
                val remote = latest?.let { runCatching { JSONObject(it.content) }.getOrNull() }
                val dirty = preferences.getBoolean(storageKey + ":dirty", false)
                val stamp = preferences.getLong(storageKey + ":stamp", 0)
                val storedId = preferences.getString(storageKey + ":id", "").orEmpty()
                if (!dirty) {
                    if (remote != null && (latest!!.createdAt > stamp || latest!!.createdAt == stamp && (storedId.isEmpty() || latest!!.id <= storedId)) && remote.opt("richPreviews") is Boolean) {
                        preferences.edit().putString(storageKey, remote.toString()).putLong(storageKey + ":stamp", latest!!.createdAt).putString(storageKey + ":id", latest!!.id).apply()
                        mutable.value = AppSettings(remote.getBoolean("richPreviews"), "Synced")
                    } else mutable.update { it.copy(sync = if (read) "Synced" else "Sync unavailable") }
                    return@launch
                }
                // Fetch first, preserve fields introduced by other app versions, then apply the local edit.
                if (!read) { mutable.update { it.copy(sync = "Sync pending") }; return@launch }
                val merged = (remote ?: document()).put("richPreviews", state.value.richPreviews)
                val timestamp = maxOf(System.currentTimeMillis() / 1000, stamp + 1, (latest?.createdAt ?: 0) + 1)
                val unsigned = Nip01Event.complete(author, timestamp, 30078, listOf(listOf("d", identifier)), merged.toString(), "")
                val signed = sign(unsigned)
                ensureActive()
                if (account != author) return@launch
                if (signed == null) { mutable.update { it.copy(sync = "Sync pending") }; return@launch }
                val accepted = coroutineScope { publishRoutes.map { async(Dispatchers.IO) { publish(it, signed) } }.awaitAll().any { it } }
                ensureActive()
                if (accepted) {
                    preferences.edit().putString(storageKey, merged.toString()).putLong(storageKey + ":stamp", timestamp).putString(storageKey + ":id", signed.id).putBoolean(storageKey + ":dirty", false).apply()
                }
                mutable.update { it.copy(sync = if (accepted) "Synced" else "Sync pending") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (account == author) mutable.update { it.copy(sync = "Sync unavailable") } }
        }
    }
    private suspend fun publish(url: String, event: Nip01Event): Boolean = withTimeoutOrNull(7000) {
        val result = CompletableDeferred<Boolean>()
        val socket = relay.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(JSONArray().put("EVENT").put(JSONObject(event.toJsonString())).toString()) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > 16384) return
                runCatching { val message = JSONArray(text)
                    if (message.optString(0) == "OK" && message.optString(1) == event.id) result.complete(message.optBoolean(2, false))
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { result.complete(false) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { result.complete(false) }
        })
        try { result.await() } finally { socket.cancel() }
    } ?: false
}
