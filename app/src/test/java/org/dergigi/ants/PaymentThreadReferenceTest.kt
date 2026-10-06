package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test

class PaymentThreadReferenceTest {
    private val author = "a".repeat(64)
    private val target = "b".repeat(64)
    private fun event(kind: Int, tags: List<List<String>>) = Nip01Event("c".repeat(64), author, 1, kind, tags, "", "")

    @Test fun paymentsExposeTheTargetAndPreserveRelayHints() {
        for (kind in listOf(9735, 9321)) {
            val reference = checkNotNull(threadParentReference(event(kind, listOf(listOf("p", author), listOf("e", target, "wss://example.com")))))
            assertEquals(target, reference.key)
            assertEquals(listOf("wss://example.com"), reference.relays)
        }
    }
    @Test fun addressablePaymentTargetsKeepTheirAddressFilter() {
        val reference = checkNotNull(threadParentReference(event(9735, listOf(listOf("a", "30023:$author:article")))))
        assertTrue(reference.matches(Nip01Event(target, author, 2, 30023, listOf(listOf("d", "article")), "", "")))
        assertFalse(reference.matches(Nip01Event(target, author, 2, 30023, listOf(listOf("d", "other")), "", "")))
    }
    @Test fun profileOnlyPaymentsAndInvalidReferencesHaveNoNoteHeader() {
        assertNull(threadParentReference(event(9321, listOf(listOf("p", author), listOf("e", "invalid")))))
    }
    @Test fun reactionsAndMarkedRepliesKeepTheirOriginalParentSelection() {
        val tags = listOf(listOf("e", author, "", "root"), listOf("e", target, "", "reply"))
        assertEquals(target, threadParentReference(event(1, tags))?.key)
        assertEquals(target, threadParentReference(event(7, tags))?.key)
    }
}
