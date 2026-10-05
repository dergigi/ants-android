package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test

class FileAttachmentTest {
    private fun file(content: String, tags: List<List<String>> = emptyList()) =
        Nip01Event("file", "author", 0, 1063, tags, content, "signature")

    @Test fun gifIndexJsonBecomesImageAndTitleRatherThanDescription() {
        val url = "https://media1.tenor.com/m/tkeS8kfqIZcAAAAd/fortnite-dance.gif"
        val parsed = fileAttachment(file("""{"queryKey":"fn awesome","title":"Fortnite Dance GIF","sourceUrl":"$url","mimeType":"image/gif","animated":true}""",
            listOf(listOf("url", url), listOf("m", "image/gif"))))
        assertEquals(url, parsed.image)
        assertEquals("Fortnite Dance GIF", parsed.title)
        assertEquals("", parsed.description)
        assertEquals(listOf(url), parsed.urls)
    }

    @Test fun tagsOverrideJsonAndFallbacksDoNotBecomeDuplicatePreviews() {
        val parsed = fileAttachment(file("""{"sourceUrl":"https://example.com/alternate.gif","title":"JSON title"}""",
            listOf(listOf("url", "https://example.com/main.gif"), listOf("title", "Tag title"),
                listOf("fallback", "https://example.com/main.gif"))))
        assertEquals("Tag title", parsed.title)
        assertEquals("https://example.com/main.gif", parsed.image)
        assertEquals(2, parsed.urls.size)
    }

    @Test fun ordinaryFileDescriptionsArePreservedWithoutPretendingPdfIsImage() {
        val parsed = fileAttachment(file("The documentation", listOf(listOf("url", "https://example.com/manual.pdf"), listOf("m", "application/pdf"), listOf("size", "1024"))))
        assertEquals("manual.pdf", parsed.title)
        assertEquals("The documentation", parsed.description)
        assertEquals(1024L, parsed.size)
        assertNull(parsed.image)
        assertNull(parsed.video)
    }

    @Test fun unsafeSourcesAreNotOpenedOrPreviewed() {
        val parsed = fileAttachment(file("""{"sourceUrl":"javascript:alert(1)","title":"Unsafe"}""",
            listOf(listOf("url", "https://user:pass@example.com/file.gif"))))
        assertTrue(parsed.urls.isEmpty())
        assertNull(parsed.image)
    }

    @Test fun jsonOnlyVideoGetsInlinePlayerSource() {
        val parsed = fileAttachment(file("""{"sourceUrl":"https://example.com/download?id=1","mimeType":"video/mp4","title":"Clip"}"""))
        assertEquals("https://example.com/download?id=1", parsed.video)
        assertEquals("", parsed.description)
    }

    @Test fun unrecognizedOrMalformedJsonIsNotDumpedIntoCard() {
        assertEquals("", fileAttachment(file("""{"queryKey":"unrecognized"}""")).description)
        assertEquals("", fileAttachment(file("{broken json")).description)
    }
}
