package org.dergigi.ants

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ContactsTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val owner = "c".repeat(64)
    private fun event(id: String, time: Long, tags: List<List<String>>, pubkey: String = owner) =
        Nip01Event(id, pubkey, time, 3, tags, "", "")

    @Test fun latestListReplacesRatherThanMergesAndValidatesKeys() {
        val old = event("0", 1, listOf(listOf("p", bob)))
        val latest = event("1", 2, listOf(listOf("p", alice.uppercase()), listOf("p", alice), listOf("p", "invalid"), listOf("e", bob)))
        val other = event("2", 3, listOf(listOf("p", bob)), bob)
        assertEquals(listOf(alice), contactPubkeys(checkNotNull(latestContactList(listOf(old, latest, other), owner))))
        assertEquals("1", latestContactList(listOf(latest, latest.copy(id = "2")), owner)?.id)
        assertTrue(contactPubkeys(latest.copy(tags = emptyList())).isEmpty())
    }

    @Test fun contactsExpandWithinBranchScopeAndIntersect() = runBlocking {
        var fetches = 0
        val parser = SearchQuery(owner, resolveContacts = { fetches++; listOf(alice, bob) }) { if (it == "alice") alice else bob }
        val branches = parser.parse("(by:@contacts by:alice kind:1) OR (mentions:@CONTACTS kind:30023)")
        assertEquals("[\"$alice\"]", branches[0].filter.getJSONArray("authors").toString())
        assertFalse(branches[0].filter.has("#p"))
        assertEquals("[\"$alice\",\"$bob\"]", branches[1].filter.getJSONArray("#p").toString())
        assertFalse(branches[1].filter.has("authors"))
        assertEquals(1, fetches)
        assertEquals(2, parser.parse("from:(@contacts OR alice)").single().filter.getJSONArray("authors").length())
    }

    @Test fun unavailableContactsNeverBecomeAnUnrestrictedSearch() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { SearchQuery(null, resolveContacts = { listOf(alice) }) { bob }.parse("by:@contacts") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { SearchQuery(owner, resolveContacts = { emptyList() }) { bob }.parse("mentions:@contacts") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { SearchQuery(owner, resolveContacts = { listOf(alice) }) { bob }.parse("by:@contacts by:bob") }
        }
    }
}
