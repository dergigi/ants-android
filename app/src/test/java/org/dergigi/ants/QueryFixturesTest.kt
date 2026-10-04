package org.dergigi.ants

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class QueryFixturesTest {
    private val fixtures = JSONObject(checkNotNull(javaClass.getResourceAsStream("/queries.json")).bufferedReader().use { it.readText() })
    private val now = Instant.parse(fixtures.getString("now"))
    private fun plans(query: String) = queryLeaves(query).map { compileQueryBranch(it, now) }
    private fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }

    @Test fun portableValidFixtures() {
        val valid = fixtures.getJSONArray("valid")
        for (i in 0 until valid.length()) {
            val fixture = valid.getJSONObject(i)
            val query = fixture.getString("query")
            val plans = plans(query)
            fixture.optJSONArray("searches")?.let { expected ->
                assertEquals(query, strings(expected), plans.map { it.filter.optString("search") })
            }
            fixture.optJSONArray("profiles")?.let { expected ->
                assertEquals(query, strings(expected), plans.map { it.filter.getString("search") })
                assertTrue(query, plans.all { it.filter.getJSONArray("kinds").toString() == "[0]" })
            }
            fixture.optJSONArray("filters")?.let { expected ->
                assertEquals(query, expected.length(), plans.size)
                for (j in plans.indices) {
                    val filter = JSONObject(plans[j].filter.toString())
                    // Fixtures specify web defaults; Android defaults to native renderable kinds.
                    if (!filter.has("kinds")) filter.put("kinds", fixtures.getJSONArray("defaultKinds"))
                    val wanted = expected.getJSONObject(j)
                    for (key in wanted.keys()) assertEquals("$query: $key", wanted.get(key).toString(), filter.get(key).toString())
                }
            }
            fixture.optJSONArray("authors")?.let { expected ->
                assertEquals(query, expected.length(), plans.size)
                plans.forEachIndexed { j, plan ->
                    assertEquals(query, expected.getJSONArray(j).toString(), JSONArray(plan.authors.map { JSONArray(it) }).toString())
                }
            }
        }
    }

    @Test fun portableInvalidFixtures() {
        strings(fixtures.getJSONArray("invalid")).forEach { query ->
            assertThrows(query, IllegalArgumentException::class.java) { plans(query) }
        }
    }

    @Test fun boundsAndLiteralScope() {
        assertThrows(IllegalArgumentException::class.java) { plans("(".repeat(17) + "x" + ")".repeat(17)) }
        assertThrows(IllegalArgumentException::class.java) { plans(List(6) { "(a OR b)" }.joinToString(" ")) }
        assertEquals(32, plans(List(5) { "(a OR b)" }.joinToString(" ")).size)
        assertTrue(plans("\"by:alice\"").single().authors.isEmpty())
        assertEquals(listOf(listOf("Alice Smith")), plans("by:\"Alice Smith\"").single().authors)
        assertEquals("[1]", plans("is:notes").single().filter.getJSONArray("kinds").toString())
    }
}
