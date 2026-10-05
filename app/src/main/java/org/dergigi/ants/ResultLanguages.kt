package org.dergigi.ants

import java.util.Locale

internal const val UNKNOWN_LANGUAGE = "und"
private val textLanguageKinds = setOf(1, 20, 21, 22, 1111, 9802, 30023)
private val isoLanguages = Locale.getISOLanguages().toSet()

internal data class EventLanguages(val codes: Set<String> = emptySet(), val uncertain: Boolean = true) {
    val buckets: Set<String> get() = codes + if (uncertain || codes.isEmpty()) setOf(UNKNOWN_LANGUAGE) else emptySet()
}

internal data class LanguageSelection(
    val query: String = "",
    val excluded: Set<String> = emptySet(),
    val keepUnknown: Boolean = true,
) {
    fun forQuery(value: String) = if (query == value) this else LanguageSelection(query = value)
    fun accepts(result: EventLanguages): Boolean = result.codes.any { it !in excluded } ||
        keepUnknown && (result.uncertain || result.codes.isEmpty())
}

/** Only self-labels: kind-1985 tags describe other objects, not that label event's own prose. */
internal fun declaredLanguages(event: Nip01Event): Set<String> {
    if (event.kind == 1985) return emptySet()
    val namespaces = event.tags.filter { it.firstOrNull() == "L" }.mapNotNull { it.getOrNull(1) }
    return event.tags.mapNotNull { tag ->
        if (tag.firstOrNull() != "l" || tag.getOrNull(2) != "ISO-639-1" ||
            namespaces.isNotEmpty() && "ISO-639-1" !in namespaces) return@mapNotNull null
        tag.getOrNull(1)?.lowercase(Locale.ROOT)?.takeIf { it in isoLanguages }
    }.toSet()
}

internal fun languageName(code: String): String {
    if (code == UNKNOWN_LANGUAGE) return "Unknown"
    if (code == "de") return "Deutsch"
    return Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).takeUnless { it == code || it.isBlank() } ?: code
}

/** Bounded, conservative detection. Classifier stays broad; selected languages never constrain prediction. */
internal class ResultLanguageAnalyzer(private val predict: (String) -> List<LanguagePrediction>) {
    private val cache = object : LinkedHashMap<String, EventLanguages>(600, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EventLanguages>?) = size > 600
    }
    private val codeBlocks = Regex("```[\\s\\S]*?(?:```|$)|`[^`\\n]*`")
    private val links = Regex("https?://\\S+|(?:nostr:)?(?:npub|nprofile|note|nevent|naddr)1[0-9a-z]+|(?<!\\S)@[\\w.]+", RegexOption.IGNORE_CASE)
    private val nonText = Regex("[^\\p{L}\\p{M}\\p{N}\\s.,!?;:'’\"-]")

    fun analyze(event: Nip01Event): EventLanguages? {
        if (event.kind !in textLanguageKinds || event.encryptedContent) return null
        return cache.getOrPut(event.id) {
            val declared = declaredLanguages(event)
            val text = nonText.replace(links.replace(codeBlocks.replace(event.content.take(16_000), " "), " "), " ")
            val chunks = text.lines().flatMap { line -> line.trim().chunked(400) }
                .map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.count(Char::isLetter) >= 12 }
            val samples = if (chunks.size <= 3) chunks else listOf(chunks.first(), chunks[chunks.size / 2], chunks.last())
            val detected = linkedSetOf<String>()
            var uncertain = samples.isEmpty()
            for (sample in samples) {
                val predictions = predict(sample).sortedByDescending { it.confidence }
                val best = predictions.firstOrNull()
                val next = predictions.getOrNull(1)?.confidence ?: 0.0
                if (best != null && best.confidence >= 0.80 && best.confidence - next >= 0.20) detected.add(best.code)
                else uncertain = true
            }
            // Explicit self-labels help short/ambiguous text; confident text wins over conflicting labels.
            if (detected.isEmpty() && declared.isNotEmpty()) EventLanguages(declared, uncertain = false)
            else EventLanguages(detected, uncertain)
        }
    }
}
