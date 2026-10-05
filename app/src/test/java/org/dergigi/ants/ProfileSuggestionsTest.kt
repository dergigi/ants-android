package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test

class ProfileSuggestionsTest {
    private fun person(key: String, name: String, username: String = "", nip05: String = "") =
        SuggestedProfile(key, Profile(name, "", null, 0, nip05), username)

    @Test fun contactsRankFirstThenExactAndPrefixMatches() {
        val people = listOf(person("a", "Alice"), person("b", "Malice"), person("c", "Alicia"), person("d", "Bob"))
        assertEquals(listOf("b", "a", "c"), matchingProfiles("ali", people, setOf("b")).map { it.pubkey })
        assertEquals(listOf("a", "b"), matchingProfiles("alice", people, emptySet()).map { it.pubkey })
    }
    @Test fun usernameAndNip05MatchWithoutDisplayNameAndIgnoreCase() {
        val people = listOf(person("a", "Gigi", "dergigi"), person("b", "Someone", nip05 = "alice@example.com"))
        assertEquals("a", matchingProfiles("DER", people, emptySet()).single().pubkey)
        assertEquals("b", matchingProfiles("alice@", people, emptySet()).single().pubkey)
        assertTrue(matchingProfiles("missing", people, emptySet()).isEmpty())
        assertTrue(matchingProfiles("", people, emptySet()).isEmpty())
    }
    @Test fun resultsAreDeduplicatedAndBounded() {
        val people = (0..20).map { person(it.toString(), "Alice $it") }
        val result = matchingProfiles("alice", people + people, emptySet())
        assertEquals(8, result.size)
        assertEquals(result.size, result.map { it.pubkey }.distinct().size)
    }
    @Test fun publicKeyCompletionKeepsTheOtherFilters() {
        val text = "GM (by:gi OR by:@me) since:2w"
        val token = checkNotNull(activeQueryToken(text, text.indexOf("gi") + 2))
        val choice = "by:${Nip19.npubEncode("a".repeat(64))}"
        val completed = completeQuery(text, QuerySuggestions(token.start, token.end, listOf(choice)), choice)
        assertEquals("GM ($choice OR by:@me) since:2w", completed.text)
        assertEquals('O', completed.text[completed.cursor])
    }
}
