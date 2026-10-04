package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

// Keep this list aligned with the native content renderers, not the protocol's
// full kind catalog. Metadata lookups can still fetch profiles independently.
internal val renderedKinds = setOf(0, 1, 3, 6, 7, 20, 21, 22, 1063, 1111, 1337, 1617, 1621, 1984, 9321, 9735, 9802, 10000, 10001, 10003, 30023, 39089)

// Follow lists are available explicitly without filling broad text feeds with metadata.
internal val defaultSearchKinds = renderedKinds - 3

internal fun Nip01Event.isRenderable(): Boolean {
    if (kind !in renderedKinds) return false
    if (kind in setOf(9321, 9735)) return tagValue("p")?.let(Nip19::normalizePubkey) != null &&
        (if (kind == 9735) !tagValue("bolt11").isNullOrBlank() else tags.any { it.firstOrNull() == "proof" })
    if (kind == 6) return taggedNoteReferences(this).isNotEmpty()
    if (kind == 3 || kind == 1063) return true
    if (kind in listKinds) return publicListEntries(this).isNotEmpty() ||
        (kind == 39089 && !tagValue("title").isNullOrBlank())
    if (kind == 0) return runCatching {
        val profile = JSONObject(content)
        listOf("name", "display_name", "displayName", "about").any { (profile.opt(it) as? String)?.isNotBlank() == true } ||
            (profile.opt("picture") as? String)?.startsWith("https://") == true
    }.getOrDefault(false)
    val text = content.trim()
    // A JSON code snippet is intentional code; JSON payloads in other kinds
    // use the generic structured view when explicitly requested.
    if (kind != 1337 && (text.startsWith('{') || text.startsWith('['))) {
        val payloadOnly = runCatching {
            val tokener = JSONTokener(text)
            val value = tokener.nextValue()
            (value is JSONObject || value is JSONArray) && tokener.nextClean().code == 0
        }.getOrDefault(false)
        if (payloadOnly) return false
    }
    return when (kind) {
        1984 -> tags.any { it.firstOrNull() in setOf("p", "e") && it.getOrNull(1)?.let { v -> referenceQuery(it[0], v) } != null }
        7 -> reactionTargetId(this) != null
        20 -> eventImages(this, compact = true).isNotEmpty()
        21, 22 -> eventVideos(this).isNotEmpty()
        else -> text.isNotBlank() || eventImages(this, compact = true).isNotEmpty() || eventVideos(this).isNotEmpty()
    }
}

internal fun SearchBranch.forRenderedResults(): SearchBranch? {
    val requested = filter.optJSONArray("kinds")
    val explicit = requested != null || filter.has("ids")
    val kinds = requested?.let { array -> (0 until array.length()).map { array.getInt(it) }.filter { it in 0..65535 } }
    if (kinds != null && kinds.isEmpty()) return null
    val renderedFilter = JSONObject(filter.toString())
    if (kinds != null) renderedFilter.put("kinds", JSONArray(kinds))
    else if (!filter.has("ids")) renderedFilter.put("kinds", JSONArray(defaultSearchKinds.toList()))
    // Structured relay queries can fill the result budget directly. Text-search
    // relays retain their smaller candidate limit; exact lookups need no expansion.
    if (!filter.has("search") && !filter.has("ids") && !filter.has("#d")) renderedFilter.put("limit", 500)
    return copy(filter = renderedFilter, renderedOnly = !explicit)
}
