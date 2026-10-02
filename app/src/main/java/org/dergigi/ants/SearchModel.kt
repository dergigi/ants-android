package org.dergigi.ants

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val name: String, val about: String, val picture: String?, val timestamp: Long)
data class SearchState(
    val query: String = "", val submitted: String = "", val searched: Boolean = false,
    val loading: Boolean = false, val error: String? = null,
    val events: List<Nip01Event> = emptyList(), val profiles: Map<String, Profile> = emptyMap(),
    val statuses: Map<String, String> = emptyMap(), val history: List<String> = emptyList(),
    val saved: List<String> = emptyList(), val relays: List<String> = defaultSearchRelays,
)

class SearchModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("ants", 0)
    private val relay = RelaySearch()
    private val mutable = MutableStateFlow(SearchState(history = load("history"), saved = load("saved"), relays = load("relays").ifEmpty { defaultSearchRelays }))
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0
    private fun load(key: String): List<String> = runCatching {
        val a = JSONArray(preferences.getString(key, "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    private fun persist(key: String, values: List<String>) { preferences.edit().putString(key, JSONArray(values).toString()).apply() }
    fun edit(value: String) { mutable.update { it.copy(query = value) } }
    fun clearHistory() { persist("history", emptyList()); mutable.update { it.copy(history = emptyList()) } }
    fun toggleSaved(query: String) {
        val value = query.trim(); if (value.isEmpty()) return
        val saved = state.value.saved.let { if (value in it) it - value else (listOf(value) + it).take(100) }
        persist("saved", saved); mutable.update { it.copy(saved = saved) }
    }
    fun setRelays(text: String): String? {
        val urls = text.lines().map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty() || urls.size > 12) return "Add between 1 and 12 relay URLs."
        if (urls.any { url -> runCatching { val uri = Uri.parse(url); uri.scheme != "wss" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null }.getOrDefault(true) }) return "Use secure wss:// relay URLs, one per line."
        persist("relays", urls); mutable.update { it.copy(relays = urls) }; return null
    }
    fun stop() { generation++; searchJob?.cancel(); mutable.update { it.copy(loading = false, statuses = it.statuses.mapValues { (_, v) -> if (v in listOf("Connecting", "Searching")) "Stopped" else v }) } }
    fun search(query: String = state.value.query) {
        val input = query.trim(); if (input.isBlank()) return
        searchJob?.cancel(); val current = ++generation
        mutable.update { it.copy(query = input, submitted = input, searched = true, loading = true, error = null, events = emptyList(), statuses = emptyMap()) }
        searchJob = viewModelScope.launch {
            try {
                val branches = withContext(Dispatchers.IO) { SearchQuery(relay.http).parse(input) }
                if (current != generation) return@launch
                val history = (listOf(input) + state.value.history.filter { it != input }).take(20)
                persist("history", history); mutable.update { it.copy(history = history) }
                val urls = if (branches.any { it.filter.has("search") }) state.value.relays else (state.value.relays + generalRelays).distinct()
                relay.search(branches, urls).flowOn(Dispatchers.IO).collect { update ->
                    if (current != generation) return@collect
                    when (update) {
                        is RelayUpdate.Status -> mutable.update { it.copy(statuses = it.statuses + (update.relay to update.text)) }
                        is RelayUpdate.Event -> {
                            val event = update.event
                            if (event.kind == 0) updateProfile(event)
                            mutable.update { it.copy(events = (it.events + event).distinctBy { e -> e.id }.sortedByDescending { e -> e.createdAt }.take(500)) }
                        }
                    }
                }
                if (current != generation) return@launch
                mutable.update { it.copy(loading = false) }
                val authors = state.value.events.flatMap { listOfNotNull(it.pubkey, highlightAuthor(it)) }.distinct().filter { it !in state.value.profiles }.take(200)
                if (authors.isNotEmpty()) {
                    val filter = JSONObject().put("kinds", JSONArray().put(0)).put("authors", JSONArray(authors)).put("limit", authors.size)
                    relay.search(listOf(SearchBranch(filter)), listOf("wss://purplepag.es", "wss://relay.damus.io"), 7000).flowOn(Dispatchers.IO).collect {
                        if (current == generation && it is RelayUpdate.Event) updateProfile(it.event)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) mutable.update { it.copy(loading = false, error = e.message ?: "Search failed. Check your connection and try again.") } }
        }
    }
    private fun updateProfile(event: Nip01Event) {
        runCatching {
            val json = JSONObject(event.content)
            val profile = Profile(json.optString("display_name").ifBlank { json.optString("name") }.ifBlank { event.pubkey.take(12) }, json.optString("about"), json.optString("picture").takeIf { it.startsWith("https://") }, event.createdAt)
            mutable.update { state -> if ((state.profiles[event.pubkey]?.timestamp ?: -1) > event.createdAt) state else state.copy(profiles = (state.profiles + (event.pubkey to profile)).entries.toList().takeLast(1000).associate { it.toPair() }) }
        }
    }
    override fun onCleared() { relay.http.dispatcher.cancelAll(); relay.http.connectionPool.evictAll() }
}

fun incomingQuery(value: String): String {
    val uri = Uri.parse(value)
    if (uri.host?.lowercase() in listOf("ants.sh", "www.ants.sh")) {
        uri.getQueryParameter("q")?.let { return it }
        val parts = uri.pathSegments
        if (parts.size >= 2) return when (parts[0]) { "p" -> "by:${parts[1]}"; "e" -> parts[1]; "t" -> parts[1].split(',', '+', ' ').filter { it.isNotBlank() }.joinToString(" OR ") { "#$it" }; else -> value }
    }
    return value.removePrefix("nostr:")
}
