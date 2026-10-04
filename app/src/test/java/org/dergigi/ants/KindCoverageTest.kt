package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class KindCoverageTest {
    @Test fun explicitKindsArePreservedIncludingMixedNativeAndGenericBranches() {
        val branch = checkNotNull(SearchBranch(JSONObject().put("kinds", JSONArray(listOf(1, 3, 10002, 30009, 65535)))) .forRenderedResults())
        assertEquals("[1,3,10002,30009,65535]", branch.filter.getJSONArray("kinds").toString())
        assertFalse(branch.renderedOnly)
    }

    @Test fun directIdsDoNotAcquireADefaultKindRestriction() {
        val branch = checkNotNull(SearchBranch(JSONObject().put("ids", JSONArray().put("a".repeat(64)))).forRenderedResults())
        assertFalse(branch.filter.has("kinds"))
        assertFalse(branch.renderedOnly)
    }

    @Test fun broadQueriesRetainReadableDefaults() {
        val branch = checkNotNull(SearchBranch(JSONObject().put("search", "nostr")).forRenderedResults())
        assertTrue(branch.renderedOnly)
        val kinds = branch.filter.getJSONArray("kinds")
        assertEquals(defaultSearchKinds, (0 until kinds.length()).map(kinds::getInt).toSet())
    }

    @Test fun filesAndEmptyFollowListsCanBeDisplayed() {
        val event = Nip01Event("", "", 0, 1063, listOf(listOf("url", "https://example.com/manual.pdf"), listOf("m", "application/pdf")), "", "")
        assertTrue(event.isRenderable())
        assertTrue(event.copy(kind = 3, tags = emptyList()).isRenderable())
        assertNull(attachmentUrl("javascript:alert(1)"))
        assertNull(attachmentUrl("https://user:password@example.com/file"))
        assertEquals("https://example.com/manual.pdf", attachmentUrl("https://example.com/manual.pdf"))
    }
}
