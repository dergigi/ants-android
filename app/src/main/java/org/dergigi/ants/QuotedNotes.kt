package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject

internal data class QuoteReference(val key: String, val query: String, val filter: JSONObject, val relays: List<String>) {
    fun matches(event: Nip01Event): Boolean = SearchBranch(filter).accepts(event)
}

internal fun quoteReference(query: String): QuoteReference? = runCatching {
    val value = query.removePrefix("nostr:")
    when {
        value.startsWith("note1", true) -> {
            val id = Nip19.noteDecode(value)
            QuoteReference(id, Nip19.noteEncode(id), JSONObject().put("ids", JSONArray().put(id)).put("limit", 1), emptyList())
        }
        value.startsWith("nevent1", true) -> {
            val pointer = Nip19.neventDecode(value)
            QuoteReference(pointer.eventId, value, JSONObject().put("ids", JSONArray().put(pointer.eventId)).put("limit", 1), pointer.relays)
        }
        value.startsWith("naddr1", true) -> {
            val pointer = Nip19.naddrDecode(value)
            QuoteReference(pointer.coordinate, value, JSONObject().put("authors", JSONArray().put(pointer.pubkey))
                .put("kinds", JSONArray().put(pointer.kind)).put("#d", JSONArray().put(pointer.identifier)).put("limit", 1), pointer.relays)
        }
        else -> null
    }
}.getOrNull()

internal fun quoteReferences(event: Nip01Event): List<QuoteReference> {
    val inline = contentLinks(event.content, event).mapNotNull { quoteReference(it.query) }
    val tagged = event.tags.mapNotNull { tag ->
        val type = tag.firstOrNull()
        if (type != "q" && !(type == "e" && tag.getOrNull(3) == "mention")) return@mapNotNull null
        val query = tag.getOrNull(1)?.let { referenceQuery(type, it) } ?: return@mapNotNull null
        quoteReference(query)?.let { it.copy(relays = it.relays + listOfNotNull(tag.getOrNull(2))) }
    }
    // A nevent and a q tag referring to the same event produce one embed.
    return (inline + tagged).groupBy { it.key }.values.take(8).map { references ->
        references.first().copy(relays = references.flatMap { it.relays }.distinct())
    }
}

internal fun withoutEmbeddedQuotes(text: String, event: Nip01Event, references: List<QuoteReference>): String {
    val keys = references.map { it.key }.toSet()
    if (keys.isEmpty()) return text
    val hidden = contentLinks(text, event).filter { quoteReference(it.query)?.key in keys }
    val result = StringBuilder()
    var cursor = 0
    for (link in hidden) {
        result.append(text, cursor, link.start)
        cursor = link.end
    }
    result.append(text, cursor, text.length)
    return result.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
}
