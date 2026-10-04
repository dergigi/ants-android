package org.dergigi.ants

import org.json.JSONObject
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
    fun people(keys: List<String>): String {
        if (contactKeys.isNotEmpty() && keys.toHashSet().containsAll(contactKeys)) {
            return (listOf("@contacts") + keys.filterNot { it in contactKeys }.map(Nip19::npubEncode)).joinToString(",")
        }
        return keys.take(20).joinToString(",", transform = Nip19::npubEncode) +
            if (keys.size > 20) " … (${keys.size} profiles)" else ""
    }
    return buildList {
        values("ids").forEach { add("nostr:${Nip19.noteEncode(it)}") }
        if (kinds.isNotEmpty()) add("kind:${kinds.joinToString(",")}")
        if (authors.isNotEmpty()) add("by:${people(authors)}")
        values("#p").takeIf { it.isNotEmpty() }?.let { add("mentions:${people(it)}") }
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
    return runCatching {
        queryLeaves(input).joinToString("\nOR ") { leaves ->
            val plan = compileQueryBranch(leaves, now)
            val filters = SearchBranch(plan.filter).queryTranslation()
            fun person(value: String): String {
                if (value.equals("@contacts", true)) return "@contacts"
                val key = resolved[value] ?: (if (value.equals("@me", true)) identity else Nip19.normalizePubkey(value))
                return key?.let(Nip19::npubEncode) ?: JSONObject.quote(value)
            }
            fun clause(field: String, values: List<String>) = if (values.size == 1) "$field:${person(values.single())}"
                else "$field:(" + values.joinToString(" OR ") { person(it) } + ")"
            (listOf(filters) + plan.authors.map { clause("by", it) } + listOfNotNull(plan.mentions?.let { clause("mentions", it) }))
                .filter(String::isNotBlank).joinToString(" ")
        }
    }.getOrElse { input }

}

private fun translatedDate(timestamp: Long, name: String): String {
    val date = Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC)
    val dayBound = date.toLocalTime().toSecondOfDay() == if (name == "since") 0 else 86399
    return if (dayBound) date.toLocalDate().toString() else Instant.ofEpochSecond(timestamp).toString()
}
