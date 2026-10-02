package org.dergigi.ants

import org.json.JSONObject

internal data class ProfileFields(val name: String, val display: String, val about: String, val nip05: String)
internal fun profileFields(event: Nip01Event): ProfileFields {
    val json = runCatching { JSONObject(event.content) }.getOrDefault(JSONObject())
    fun string(key: String) = (json.opt(key) as? String).orEmpty()
    return ProfileFields(string("name"), string("display_name").ifBlank { string("displayName") }, string("about"),
        string("nip05").ifBlank { json.optJSONObject("nip05")?.optString("url").orEmpty() })
}
internal fun normalizedNip05(value: String): String {
    val clean = value.trim().lowercase().removePrefix("@")
    if (clean.isBlank()) return ""
    return if ('@' in clean) clean else "_@$clean"
}
internal fun profileMatchScore(term: String, event: Nip01Event): Int {
    val f = profileFields(event)
    val q = term.lowercase()
    val names = listOf(f.name.lowercase(), f.display.lowercase())
    var score = when { q in names -> 40; names.any { it.startsWith(q) } -> 30; names.any { q in it } -> 20; else -> 0 }
    if (q in f.about.lowercase()) score += 10
    val n5 = normalizedNip05(f.nip05)
    if (n5.isNotBlank()) {
        val local = n5.substringBefore('@'); val domain = n5.substringAfter('@')
        score += if (local == "_" || local.isEmpty()) when {
            domain == q -> 120; domain.startsWith(q) -> 90; q in domain -> 70; else -> 0
        } else when {
            q in listOf(n5, local, domain) -> 90
            listOf(n5, local, domain).any { it.startsWith(q) } -> 70
            listOf(n5, local, domain).any { q in it } -> 50
            else -> 0
        }
    }
    return score
}
internal fun authorMatchScore(input: String, event: Nip01Event): Int {
    val raw = input.trim().lowercase(); val q = raw.removePrefix("@")
    if (q.isBlank()) return 0
    val f = profileFields(event)
    var score = listOf(f.name, f.display).sumOf { field ->
        val name = field.trim().lowercase()
        when { name == raw || name == q -> 500; name.startsWith(q) -> 120; else -> 0 }
    }
    val n5 = normalizedNip05(f.nip05)
    if (n5.isNotBlank()) {
        val root = n5.substringBefore('@') == "_"
        val local = n5.substringBefore('@').takeUnless { root }.orEmpty()
        val domain = n5.substringAfter('@')
        if (('.' in raw || '@' in raw) && n5 == normalizedNip05(raw)) score += 700
        if (local.isNotEmpty() && (local == raw || local == q)) score += 650
        if (domain == raw || domain == q) score += if (root) 650 else 550
        if (local.isNotEmpty() && local.startsWith(q)) score += 140
        if (domain.startsWith("$q.")) score += if (root) 700 else 220
        else if (domain.startsWith(q)) score += if (root) 420 else 120
    }
    return score
}
