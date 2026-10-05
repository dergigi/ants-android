package org.dergigi.ants

import org.json.JSONObject

/** Additional NIP-50 hints never replace the broad query: unsupported extensions and unknown languages remain recoverable. */
internal fun languageSearchPlan(branches: List<SearchBranch>, languages: Set<String>): List<SearchBranch> {
    val codes = languages.filter { it.matches(Regex("[a-z]{2}")) }.sorted().take(4)
    if (codes.isEmpty()) return branches
    val extraBudget = (32 - branches.size).coerceIn(0, 8)
    val extra = branches.asSequence().filter { branch ->
        branch.language == null && !branch.filter.has("ids") && !branch.filter.has("#d") &&
            branch.filter.has("search") && branch.filter.optJSONArray("kinds")?.let { kinds ->
                kinds.length() != 1 || kinds.optInt(0) != 0
            } != false
    }.flatMap { branch -> codes.asSequence().map { code ->
        val filter = JSONObject(branch.filter.toString())
        filter.put("search", "${filter.getString("search")} language:$code")
        branch.copy(filter = filter, language = code)
    } }.take(extraBudget).toList()
    // Keep broad requests first so optional hints cannot consume their entire deadline.
    return branches + extra
}
