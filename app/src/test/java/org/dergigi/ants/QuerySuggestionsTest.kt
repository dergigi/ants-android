package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test

class QuerySuggestionsTest {
    private val choices = listOf("is:blogpost", "is:file", "is:note", "is:notes", "has:gif", "site:github")
    private fun hints(text: String, cursor: Int = text.length) = querySuggestions(text, cursor, registered = choices)

    @Test fun colonListsRegisteredKindsAndPrefixNarrowsThem() {
        assertEquals(choices.take(4), hints("is:")?.choices)
        assertEquals(listOf("is:blogpost"), hints("IS:bl")?.choices)
        assertEquals(listOf("has:gif"), hints("has:")?.choices)
    }

    @Test fun registeredKindsAndSharedAliasesStayDiscoverable() {
        assertTrue(registeredQuerySuggestions.containsAll(kindAliases.keys.map { "is:$it" }))
        assertTrue(registeredQuerySuggestions.containsAll(listOf("has:gif", "site:github", "nip:01")))
    }

    @Test fun completesCurrentTokenAndKeepsCompoundQuery() {
        val text = "coffee (is:bl OR is:file) by:dergigi"
        val suggestion = checkNotNull(hints(text, text.indexOf("bl") + 2))
        val completed = completeQuery(text, suggestion, "is:blogpost")
        assertEquals("coffee (is:blogpost OR is:file) by:dergigi", completed.text)
        assertEquals('O', completed.text[completed.cursor])
    }

    @Test fun editingInsideTokenReplacesItsRemainingCharacters() {
        val text = "is:notes since:2w"
        val completed = completeQuery(text, checkNotNull(hints(text, 5)), "is:note")
        assertEquals("is:note since:2w", completed.text)
        assertEquals(8, completed.cursor)
    }

    @Test fun keepsClosingParenthesisAndAddsSpaceAtEnd() {
        val grouped = "(is:bl)"
        assertEquals("(is:blogpost)", completeQuery(grouped, checkNotNull(hints(grouped, grouped.length - 1)), "is:blogpost").text)
        val completed = completeQuery("is:bl", checkNotNull(hints("is:bl")), "is:blogpost")
        assertEquals("is:blogpost ", completed.text)
        assertEquals(completed.text.length, completed.cursor)
        assertNull(hints(completed.text))
    }

    @Test fun ignoresQuotedTextUrlsAndSlashCommands() {
        assertNull(hints("\"something is:bl"))
        assertNull(hints("https://example.com/is:bl"))
        assertNull(hints("/help is:"))
        assertNull(hints("is:\"bl\"", 3))
        assertNull(querySuggestions("is:blogpost", 3, 10, choices))
    }

    @Test fun accountShortcutsOnlyAppearWhenLoggedIn() {
        for (text in listOf("by:", "by:@", "by:@m", "by:@c")) {
            assertNull(querySuggestions(text, text.length))
        }
        assertEquals(listOf("by:@me", "by:@contacts"), querySuggestions("by:", 3, loggedIn = true)?.choices)
        assertEquals(listOf("by:@me", "by:@contacts"), querySuggestions("by:@", 4, loggedIn = true)?.choices)
        assertEquals(listOf("by:@me"), querySuggestions("BY:@M", 5, loggedIn = true)?.choices)
        assertEquals(listOf("by:@contacts"), querySuggestions("by:@c", 5, loggedIn = true)?.choices)
    }

    @Test fun accountShortcutCompletionPreservesSurroundingQuery() {
        val text = "GM (by:@c OR by:dergigi) since:2w"
        val suggestion = checkNotNull(querySuggestions(text, text.indexOf("@c") + 2, loggedIn = true))
        val completed = completeQuery(text, suggestion, "by:@contacts")
        assertEquals("GM (by:@contacts OR by:dergigi) since:2w", completed.text)
        assertEquals('O', completed.text[completed.cursor])
        assertNull(querySuggestions("\"by:@m", 6, loggedIn = true))
        assertNull(querySuggestions("https://example.com/by:@m", 24, loggedIn = true))
    }

    @Test fun mentionsShortcutsRequireLoginAndSupportPartialTokens() {
        assertNull(querySuggestions("mentions:", 9))
        assertEquals(listOf("mentions:@me", "mentions:@contacts"), querySuggestions("mentions:", 9, loggedIn = true)?.choices)
        assertEquals(listOf("mentions:@contacts"), querySuggestions("mentions:@c", 11, loggedIn = true)?.choices)
    }

    @Test fun personTokensSupportNamesAndAddressesWithoutMatchingUrlsOrQuotes() {
        val text = "GM (by:alice@example.com OR p:日本)"
        val token = checkNotNull(activeQueryToken(text, text.indexOf("@example") + 3))
        assertEquals("by", token.field)
        assertEquals("alice@ex", token.value)
        assertEquals("by:alice@example.com", text.substring(token.start, token.end))
        assertEquals("日本", activeQueryToken(text, text.length - 1)?.value)
        assertNull(activeQueryToken("https://example.com/p:gi", 23))
        assertNull(activeQueryToken("by:\"gi\"", 6))
        assertNull(activeQueryToken("p:gi", 2, 4))
    }

}
