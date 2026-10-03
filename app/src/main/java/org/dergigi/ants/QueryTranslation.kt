package org.dergigi.ants

import java.time.Instant
import java.time.ZoneOffset

/** Describe the already-resolved query; never resolve identities or sign from the UI. */
internal fun SearchBranch.queryTranslation(): String {
    fun values(key: String): List<String> = filter.optJSONArray(key)?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    }.orEmpty()
    val authors = values("authors")
    val kinds = values("kinds")
    val identifiers = values("#d")
    if (identifiers.size == 1 && authors.size == 1 && kinds.size == 1) {
        return "nostr:" + Nip19.naddrEncode(NaddrPointer(identifiers.single(), authors.single(), kinds.single().toInt()))
    }
    return buildList {
        values("ids").forEach { add("nostr:${Nip19.noteEncode(it)}") }
        kinds.forEach { add("kind:$it") }
        authors.forEach { add("by:${Nip19.npubEncode(it)}") }
        values("#p").forEach { add("mentions:${Nip19.npubEncode(it)}") }
        values("#t").forEach { add("#$it") }
        for (name in listOf("since", "until")) if (filter.has(name)) {
            val timestamp = filter.getLong(name)
            val date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC)
            // Preserve exact hour offsets; calendar-day bounds stay compact.
            val dayBound = if (name == "since") date.toLocalTime().toSecondOfDay() == 0 else date.toLocalTime().toSecondOfDay() == 86399
            add("$name:${if (dayBound) date.toLocalDate().toString() else Instant.ofEpochSecond(timestamp).toString()}")
        }
        media?.let { add("has:$it") }
        site?.let { add("site:$it") }
        filter.optString("search").takeIf(String::isNotBlank)?.let { add(it) }
    }.joinToString(" ")
}
