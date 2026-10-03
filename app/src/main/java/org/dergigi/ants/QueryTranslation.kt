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
            add("$name:${translatedDate(filter.getLong(name), name)}")
        }
        media?.let { add("has:$it") }
        site?.let { add("site:$it") }
        filter.optString("search").takeIf(String::isNotBlank)?.let { add(it) }
    }.joinToString(" ")
}

/** Immediate local expansion while network-dependent names are still resolving. */
internal fun queryPreview(input: String, identity: String?, now: Instant, resolved: Map<String, String> = emptyMap()): String {
    if (input.startsWith('/')) return input
    return Regex("\"[^\"]*\"|\\S+").findAll(input.take(2000)).joinToString(" ") { match ->
        val token = match.value
        val prefix = token.substringBefore(':').lowercase()
        val value = token.substringAfter(':', "")
        when {
            token.equals("OR", true) -> "\nOR"
            prefix == "is" -> kindAliases[value.lowercase()]?.joinToString(" ") { "kind:$it" } ?: token
            prefix in listOf("by", "from", "mentions") -> {
                val key = resolved[value] ?: (if (value.equals("@me", true)) identity else Nip19.normalizePubkey(value))
                "${if (prefix == "from") "by" else prefix}:${key?.let(Nip19::npubEncode) ?: value}"
            }
            prefix in listOf("since", "until") -> runCatching {
                "$prefix:${translatedDate(searchDateTimestamp(value, prefix, now), prefix)}"
            }.getOrDefault(token)
            prefix == "site" -> "site:${when (value) { "yt" -> "youtube.com"; "gh" -> "github.com"; else -> value }}"
            prefix == "p" -> "kind:0 $value"
            else -> token
        }
    }.replace(" \nOR", "\nOR")
}

private fun translatedDate(timestamp: Long, name: String): String {
    val date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC)
    val dayBound = date.toLocalTime().toSecondOfDay() == if (name == "since") 0 else 86399
    return if (dayBound) date.toLocalDate().toString() else Instant.ofEpochSecond(timestamp).toString()
}
