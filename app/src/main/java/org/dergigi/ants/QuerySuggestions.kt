package org.dergigi.ants

import java.util.Locale

internal data class QuerySuggestions(val start: Int, val end: Int, val choices: List<String>)
internal data class CompletedQuery(val text: String, val cursor: Int)

/** Use the parser's aliases so new registered keywords automatically appear in the editor. */
internal val registeredQuerySuggestions: List<String> by lazy {
    (queryAliases.keys + kindAliases.keys.map { "is:$it" }).distinct().sorted()
}

internal fun querySuggestions(text: String, cursor: Int, selectionEnd: Int = cursor,
    registered: List<String> = registeredQuerySuggestions): QuerySuggestions? {
    if (cursor != selectionEnd || cursor !in 0..text.length || text.length > 2000 || text.trimStart().startsWith('/')) return null
    var quoted = false
    var escaped = false
    for (char in text.take(cursor)) {
        if (escaped) { escaped = false; continue }
        if (char == '\\' && quoted) { escaped = true; continue }
        if (char == '"') quoted = !quoted
    }
    if (quoted) return null
    fun boundary(char: Char) = char.isWhitespace() || char == '(' || char == ')'
    var start = cursor
    while (start > 0 && !boundary(text[start - 1])) start--
    val prefix = text.substring(start, cursor).lowercase(Locale.ROOT)
    if (!prefix.matches(Regex("[a-z]+:[a-z0-9_-]*"))) return null
    var end = cursor
    while (end < text.length && !boundary(text[end])) end++
    // Never replace quoted field values or URL-like tokens.
    if (text.substring(start, end).any { it == '"' || it == '/' || it == '\\' }) return null
    val choices = registered.filter { it.startsWith(prefix) }
    return choices.takeIf { it.isNotEmpty() }?.let { QuerySuggestions(start, end, it) }
}

internal fun completeQuery(text: String, suggestion: QuerySuggestions, choice: String): CompletedQuery {
    require(choice in suggestion.choices)
    val suffix = text.substring(suggestion.end)
    val separator = if (suffix.isEmpty() || !suffix.first().isWhitespace() && suffix.first() != ')') " " else ""
    val completed = text.substring(0, suggestion.start) + choice + separator + suffix
    val cursor = suggestion.start + choice.length + separator.length + if (suffix.firstOrNull()?.isWhitespace() == true) 1 else 0
    return CompletedQuery(completed, cursor)
}
