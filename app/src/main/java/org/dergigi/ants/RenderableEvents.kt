package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

// Keep this list aligned with the native content renderers, not the protocol's
// full kind catalog. Metadata lookups can still fetch profiles independently.
internal val renderedKinds = setOf(0, 1, 7, 20, 21, 22, 1063, 1111, 1337, 9802, 30023)

internal fun Nip01Event.isRenderable(): Boolean {
    if (kind !in renderedKinds) return false
    if (kind == 0) return runCatching {
        val profile = JSONObject(content)
        listOf("name", "display_name", "displayName", "about").any { (profile.opt(it) as? String)?.isNotBlank() == true } ||
            (profile.opt("picture") as? String)?.startsWith("https://") == true
    }.getOrDefault(false)
    val text = content.trim()
    // A JSON code snippet is intentional code; JSON payloads in other kinds
    // are not a readable note and should never become a fallback event card.
    if (kind != 1337 && (text.startsWith('{') || text.startsWith('['))) {
        val payloadOnly = runCatching {
            val tokener = JSONTokener(text)
            val value = tokener.nextValue()
            (value is JSONObject || value is JSONArray) && tokener.nextClean().code == 0
        }.getOrDefault(false)
        if (payloadOnly) return false
    }
    return when (kind) {
        7 -> reactionTargetId(this) != null
        20 -> eventImages(this, compact = true).isNotEmpty()
        21, 22 -> eventVideos(this).isNotEmpty()
        1063 -> eventImages(this, compact = true).isNotEmpty() || eventVideos(this).isNotEmpty()
        else -> text.isNotBlank() || eventImages(this, compact = true).isNotEmpty() || eventVideos(this).isNotEmpty()
    }
}

internal fun SearchBranch.forRenderedResults(): SearchBranch? {
    val requested = filter.optJSONArray("kinds")
    val kinds = if (requested == null) renderedKinds.toList()
        else (0 until requested.length()).map { requested.getInt(it) }.filter { it in renderedKinds }
    if (kinds.isEmpty()) return null
    return copy(filter = JSONObject(filter.toString()).put("kinds", JSONArray(kinds)), renderedOnly = true)
}
