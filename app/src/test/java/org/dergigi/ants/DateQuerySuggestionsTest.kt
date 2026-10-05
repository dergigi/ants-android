package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class DateQuerySuggestionsTest {
    @Test fun relativeDatesAreScopedToTheActiveDateField() {
        assertEquals(relativeDateSuggestions.map { "since:$it" }, dateSuggestions(QueryToken(0, 6, "since", "")))
        assertEquals(listOf("until:12h", "until:1d", "until:1w", "until:1m", "until:1y"), dateSuggestions(QueryToken(0, 7, "until", "1")))
        assertTrue(dateSuggestions(QueryToken(0, 6, "since", "2026-")).isEmpty())
        assertTrue(dateSuggestions(QueryToken(0, 3, "by", "")).isEmpty())
    }
    @Test fun selectedCalendarDateIsUtcRegardlessOfDeviceTimezone() {
        assertEquals("2026-10-05", queryDate(Instant.parse("2026-10-05T00:00:00Z").toEpochMilli()))
        assertEquals("2026-10-05", queryDate(Instant.parse("2026-10-05T23:59:59Z").toEpochMilli()))
    }
    @Test fun completesDatesInsideCompoundQueries() {
        val text = "GM (since:2026-01-01 OR by:@me) until:1d"
        val token = checkNotNull(activeQueryToken(text, text.indexOf("2026") + 2))
        val choice = "since:2026-10-05"
        val result = completeQuery(text, QuerySuggestions(token.start, token.end, listOf(choice)), choice)
        assertEquals("GM (since:2026-10-05 OR by:@me) until:1d", result.text)
        assertEquals('O', result.text[result.cursor])
    }
}
