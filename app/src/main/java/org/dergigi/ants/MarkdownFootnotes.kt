package org.dergigi.ants

internal data class MarkdownFootnote(val number: Int, val markdown: String)
internal data class FootnoteDocument(val body: String, val notes: List<MarkdownFootnote>)

/** Adapted from Boris's footnote expansion, with local links for the reader. */
internal object MarkdownFootnotes {
    private val reference = Regex("""(?<!\\)\[\^([^\]\s]+)](?!:)""")
    private val definition = Regex("""^ {0,3}\[\^([^\]\s]+)]:[ \t]*(.*)$""")
    private val code = Regex("""(?s)(`{3,}|~{3,})[^\n]*\n.*?\1|(`+)[^`]*?\2""")
    private const val superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    fun number(value: Int) = value.toString().map { superscripts[it - '0'] }.joinToString("")

    fun parse(markdown: String): FootnoteDocument {
        if (!markdown.contains("[^")) return FootnoteDocument(markdown, emptyList())
        // Keep code examples literal, including references and definitions inside fences.
        val slots = mutableListOf<String>()
        val marker = "\u0000${java.util.UUID.randomUUID()}-"
        val protected = code.replace(markdown) { match ->
            slots.add(match.value)
            "$marker${slots.lastIndex}\u0000"
        }
        fun restore(value: String): String {
            var result = value
            slots.forEachIndexed { index, code -> result = result.replace("$marker$index\u0000", code) }
            return result
        }
        val lines = protected.replace("\r\n", "\n").split('\n')
        val body = mutableListOf<String>()
        val definitions = linkedMapOf<String, String>()
        fun continuation(line: String) = line.startsWith("    ") || line.startsWith('\t')
        var i = 0
        while (i < lines.size) {
            val match = definition.matchEntire(lines[i])
            if (match == null) { body.add(lines[i++]); continue }
            val text = mutableListOf(match.groupValues[2])
            i++
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank()) {
                    var next = i + 1
                    while (next < lines.size && lines[next].isBlank()) next++
                    if (next < lines.size && continuation(lines[next])) { text.add(""); i++; continue }
                    break
                }
                if (!continuation(line)) break
                text.add(if (line.startsWith('\t')) line.drop(1) else line.drop(4))
                i++
            }
            definitions.putIfAbsent(match.groupValues[1].lowercase(), text.joinToString("\n").trim())
        }
        if (definitions.isEmpty()) return FootnoteDocument(markdown, emptyList())
        val order = linkedMapOf<String, Int>()
        fun references(text: String) = reference.replace(text) { match ->
            val id = match.groupValues[1].lowercase()
            if (id !in definitions) match.value else {
                val n = order.getOrPut(id) { order.size + 1 }
                "[${number(n)}](#ants-footnote-$n)"
            }
        }
        val content = references(body.joinToString("\n").trimEnd())
        // Preserve definitions even if the publisher omitted their inline reference.
        definitions.keys.forEach { order.getOrPut(it) { order.size + 1 } }
        val notes = order.map { (id, n) -> MarkdownFootnote(n, restore(references(definitions.getValue(id)))) }
        return FootnoteDocument(restore(content), notes)
    }
}
