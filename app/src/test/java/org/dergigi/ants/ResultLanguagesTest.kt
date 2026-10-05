package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResultLanguagesTest {
    private fun event(content: String = "A sufficiently long example sentence.", tags: List<List<String>> = emptyList(), kind: Int = 1) =
        Nip01Event("event", "author", 0, kind, tags, content, "signature")

    @Test fun acceptsOnlyNamespacedSelfLabels() {
        assertEquals(setOf("en"), declaredLanguages(event(tags = listOf(listOf("L", "ISO-639-1"), listOf("l", "en", "ISO-639-1")))))
        assertTrue(declaredLanguages(event(tags = listOf(listOf("l", "en")))).isEmpty())
        assertTrue(declaredLanguages(event(tags = listOf(listOf("L", "other"), listOf("l", "en", "ISO-639-1")))).isEmpty())
        assertTrue(declaredLanguages(event(tags = listOf(listOf("l", "en", "ISO-639-1")), kind = 1985)).isEmpty())
    }

    @Test fun shortNotesStayUnknownUnlessSelfLabelled() {
        val detector = ResultLanguageAnalyzer { error("Too short to classify") }
        assertEquals(EventLanguages(), detector.analyze(event("GM")))
        val labelled = ResultLanguageAnalyzer { error("Too short to classify") }
        assertEquals(EventLanguages(setOf("de"), false), labelled.analyze(event("GM", listOf(listOf("l", "de", "ISO-639-1")))))
    }

    @Test fun confidentTextTakesPrecedenceOverConflictingLabel() {
        val detector = ResultLanguageAnalyzer { listOf(LanguagePrediction("ja", .95), LanguagePrediction("en", .03)) }
        assertEquals(EventLanguages(setOf("ja"), false), detector.analyze(event(tags = listOf(listOf("l", "en", "ISO-639-1")))))
    }

    @Test fun uncertaintyAndMixedLanguagesAreRetainedConservatively() {
        val detector = ResultLanguageAnalyzer { text ->
            if (text.startsWith("English")) listOf(LanguagePrediction("en", .95)) else listOf(LanguagePrediction("ja", .55))
        }
        val detected = checkNotNull(detector.analyze(event("English paragraph with enough letters.\nUncertain paragraph with enough letters.")))
        assertEquals(setOf("en"), detected.codes)
        assertTrue(detected.uncertain)
        assertTrue(LanguageSelection(excluded = setOf("en")).accepts(detected))
        assertFalse(LanguageSelection(excluded = setOf("en"), keepUnknown = false).accepts(detected))
    }

    @Test fun codeAndLinksAreNotClassifiedAsProse() {
        val detector = ResultLanguageAnalyzer { error("No prose to classify") }
        assertEquals(EventLanguages(), detector.analyze(event("```some programming code with many letters``` https://example.com/long-language-like-url")))
    }

    @Test fun protocolRecordsAreNotLanguageFiltered() {
        assertNull(ResultLanguageAnalyzer { error("Not text") }.analyze(event(kind = 0)))
    }

    @Test fun choicesDoNotLeakIntoAnotherQuery() {
        val selected = LanguageSelection("coffee", setOf("ja"), false)
        assertEquals(selected, selected.forQuery("coffee"))
        assertEquals(LanguageSelection("tea"), selected.forQuery("tea"))
    }

    @Test fun relayHintsKeepOriginalAndDoNotMutateIt() {
        val branch = SearchBranch(JSONObject().put("search", "coffee").put("kinds", JSONArray(listOf(1))))
        val plan = languageSearchPlan(listOf(branch), setOf("en", "de"))
        assertSame(branch, plan.first())
        assertEquals("coffee", branch.filter.getString("search"))
        assertEquals(setOf("coffee language:de", "coffee language:en"), plan.drop(1).map { it.filter.getString("search") }.toSet())
    }

    @Test fun explicitLanguageAndDirectLookupsAreNotOverridden() {
        val explicit = SearchBranch(JSONObject().put("search", "coffee language:ja"), language = "ja")
        val direct = SearchBranch(JSONObject().put("ids", JSONArray(listOf("event"))))
        val profile = SearchBranch(JSONObject().put("search", "alice").put("kinds", JSONArray(listOf(0))))
        val branches = listOf(explicit, direct, profile)
        assertEquals(branches, languageSearchPlan(branches, setOf("en")))
    }

    @Test fun supplementalBranchesStayWithinBudget() {
        val branches = (1..30).map { SearchBranch(JSONObject().put("search", "term$it")) }
        val plan = languageSearchPlan(branches, setOf("en", "de", "es", "ru", "fr"))
        assertEquals(32, plan.size)
        assertEquals(branches, plan.take(30))
    }
}
