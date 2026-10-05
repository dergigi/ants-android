package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test

class LinkPreviewsTest {
    @Test fun openGraphWinsAndRelativeImageResolvesWithoutExecutingMarkup() {
        val html = """<title>Fallback</title><meta property="og:title" content="A &amp; B">
            <meta name="twitter:title" content="Other"><meta property="og:description" content="  Two   words ">
            <meta property="og:image" content="../cover.jpg"><script>throw 'unused'</script>"""
        val result = parseLinkPreview(html.toByteArray(), "https://example.com/post/article")!!
        assertEquals("A & B", result.title)
        assertEquals("Two words", result.description)
        assertEquals("https://example.com/cover.jpg", result.image)
    }
    @Test fun plainHtmlAndTwitterMetadataHaveFallbacks() {
        val result = parseLinkPreview("<title>Page</title><meta name='twitter:description' content='Summary'>".toByteArray(), "https://example.com/")!!
        assertEquals("Page", result.title)
        assertEquals("Summary", result.description)
        assertNull(result.image)
        assertNull(parseLinkPreview("<body>Nothing</body>".toByteArray(), "https://example.com/"))
    }
    @Test fun privateAndUnsafeTargetsAreRejectedIncludingPreviewImages() {
        listOf("http://example.com", "https://localhost/", "https://127.0.0.1/", "https://192.168.1.2/", "https://[::1]/", "https://user:password@example.com/", "https://example.com:8443/").forEach { assertNull(it, previewUrl(it)) }
        val result = parseLinkPreview("<title>Page</title><meta property='og:image' content='https://10.0.0.1/secret'>".toByteArray(), "https://example.com/")!!
        assertNull(result.image)
    }
    @Test fun renderedMediaIsSkippedAndOnlyFirstPageIsSelected() {
        assertEquals("https://example.com/page", firstPreviewUrl("https://example.com/image.gif https://example.com/page https://example.com/other", emptyList()))
        assertNull(firstPreviewUrl("https://example.com/image", listOf("https://example.com/image")))
    }
}
