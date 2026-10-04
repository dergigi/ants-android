package org.dergigi.ants

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class ParallelResolutionTest {
    private fun key(index: Int) = index.toString(16).padStart(64, '0')

    @Test fun exampleAuthorsStartTogetherAndBranchesRetainOrder() = runBlocking {
        withTimeout(2000) {
            val started = Channel<String>(Channel.UNLIMITED)
            val gates = listOf("jeffg", "futurepaul", "franzap").associateWith { CompletableDeferred<Unit>() }
            val keys = gates.keys.mapIndexed { i, name -> name to key(i + 1) }.toMap()
            val parser = SearchQuery { name -> started.send(name); gates.getValue(name).await(); keys.getValue(name) }
            val work = async { parser.parse("NIP-EE by:jeffg OR NIP-EE by:futurepaul OR NIP-EE by:franzap") }
            assertEquals(gates.keys, List(3) { started.receive() }.toSet())
            gates.values.toList().reversed().forEach { it.complete(Unit) }
            val branches = work.await()
            assertEquals(keys.values.toList(), branches.map { it.filter.getJSONArray("authors").getString(0) })
            assertTrue(branches.all { it.filter.getString("search") == "NIP-EE" })
        }
    }

    @Test fun concurrencyIsBoundedAndRepeatedNamesShareWork() = runBlocking {
        withTimeout(2000) {
            val started = Channel<String>(Channel.UNLIMITED)
            val release = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            var active = 0
            var peak = 0
            val parser = SearchQuery { name ->
                calls += name
                active++
                peak = maxOf(peak, active)
                try { started.send(name); release.await(); key(name.removePrefix("user").toInt()) }
                finally { active-- }
            }
            val query = (1..8).joinToString(" OR ") { "by:user$it mentions:USER$it" }
            val work = async { parser.parse(query) }
            repeat(4) { started.receive() }
            assertEquals(4, active)
            assertTrue(started.tryReceive().isFailure)
            release.complete(Unit)
            assertEquals(8, work.await().size)
            assertEquals(8, calls.size)
            assertEquals(4, peak)
        }
    }

    @Test fun stoppingSearchCancelsAllActiveLookups() = runBlocking {
        withTimeout(2000) {
            val started = Channel<Unit>(Channel.UNLIMITED)
            var stopped = 0
            val parser = SearchQuery { _ ->
                try { started.send(Unit); awaitCancellation() } finally { stopped++ }
            }
            val work = async { parser.parse("by:alice OR by:bob OR by:carol") }
            repeat(3) { started.receive() }
            work.cancelAndJoin()
            assertEquals(3, stopped)
        }
    }

    @Test fun lookupFailureCancelsSiblings() = runBlocking {
        withTimeout(2000) {
            val started = CompletableDeferred<Unit>()
            var cancelled = false
            val parser = SearchQuery { name ->
                if (name == "bad") { started.await(); error("Lookup failed") }
                try { started.complete(Unit); awaitCancellation() } finally { cancelled = true }
            }
            try { parser.parse("by:good OR by:bad"); fail("Expected lookup error") }
            catch (e: IllegalStateException) { assertEquals("Lookup failed", e.message) }
            assertTrue(cancelled)
        }
    }
}
