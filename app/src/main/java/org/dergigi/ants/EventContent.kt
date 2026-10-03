package org.dergigi.ants

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class WebLink(val text: String, val start: Int, val end: Int)
internal fun webLinks(text: String): List<WebLink> = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE).findAll(text).map { match ->
    var url = match.value.trimEnd('.', ',', ';', '!', '?')
    for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
        var surplus = url.count { it == close } - url.count { it == open }
        var end = url.length
        while (end > 0 && url[end - 1] == close && surplus > 0) { end--; surplus-- }
        url = url.substring(0, end)
    }
    WebLink(url, match.range.first, match.range.first + url.length)
}.toList()

internal fun eventImages(event: Nip01Event, compact: Boolean): List<String> {
    val declared = event.tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }
    val inline = webLinks(event.content).map { it.text }.filter {
        Uri.parse(it).path.orEmpty().substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "avif", "gif")
    }
    return (declared + inline).filter { Uri.parse(it).scheme == "https" }.distinct().take(if (compact) 4 else 100)
}

internal fun withoutRenderedImages(content: String, images: List<String>): String {
    val hidden = webLinks(content).filter { it.text in images }
    val out = StringBuilder()
    var cursor = 0
    for (link in hidden) {
        out.append(content.substring(cursor, link.start)); cursor = link.end
    }
    out.append(content.substring(cursor))
    return out.toString().replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
}

private data class PreparedContent(
    val gallery: List<String>, val images: List<String>, val videos: List<VideoAttachment>,
    val text: String, val quotes: List<QuoteReference>,
)

@Composable
internal fun EventContent(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, compact: Boolean, onNavigate: (String) -> Unit) {
    if (event.kind == 6) { RepostContent(event, onNavigate); return }
    if (event.kind in listKinds) { ListContent(event, profiles, compact, onNavigate); return }
    ResolveMentionProfiles(event)
    if (event.kind == 30023) { ArticleContent(event, compact, onNavigate); return }
    val ancestors = LocalQuoteAncestors.current + event.id
    val embedQuotes = ancestors.size <= 2
    val content = if (event.kind == 0) profile?.about?.takeIf { it.isNotBlank() } ?: "Nostr profile" else event.content
    val prepared by produceState<PreparedContent?>(null, event.id, content, compact, embedQuotes) {
        value = withContext(Dispatchers.Default) {
            val gallery = eventImages(event, compact = false)
            val videos = eventVideos(event).take(if (compact) 4 else 20)
            val quotes = if (embedQuotes) quoteReferences(event).filter { it.key !in ancestors } else emptyList()
            val fullText = withoutEmbeddedQuotes(withoutRenderedImages(content, gallery + videos.map { it.url }), event, quotes)
            // maxLines alone still asks Android to shape the entire input string.
            val text = if (compact && fullText.length > 4000) fullText.take(4000) + "…" else fullText
            PreparedContent(gallery, gallery.take(if (compact) 4 else 20), videos, text, quotes)
        }
    }
    val rendered = prepared ?: return
    val galleryImages = rendered.gallery
    val images = rendered.images
    val videos = rendered.videos
    val text = rendered.text
    val openGallery = LocalOpenGallery.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val currentNavigate by rememberUpdatedState(onNavigate)
        val linked by produceState(androidx.compose.ui.text.AnnotatedString(text), text, event.id, profiles) {
            value = withContext(Dispatchers.Default) { linkedText(text, event, profiles) { currentNavigate(it) } }
        }
        if (text.isNotBlank()) CustomEmojiText(linked, event, maxLines = if (compact) 9 else Int.MAX_VALUE, emojiSize = 20.sp,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, fontFamily = if (event.kind == 1337) FontFamily.Monospace else FontFamily.Default))
        else if (images.isEmpty() && videos.isEmpty() && rendered.quotes.isEmpty()) Text("Open event to inspect its tags.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        images.forEachIndexed { index, image -> EventImage(image, compact) { openGallery(galleryImages, index) } }
        if (galleryImages.size > images.size) {
            IconButton(onClick = { openGallery(galleryImages, images.size) }) {
                BadgedBox(badge = { Badge { Text("+${galleryImages.size - images.size}") } }) { Icon(Icons.Outlined.PhotoLibrary, "View all ${galleryImages.size} images") }
            }
        }
        videos.forEach { video -> key(video.url) { EventVideo(video) } }
        rendered.quotes.forEach { reference -> key(reference.key) { EmbeddedNote(reference, ancestors, onNavigate) } }
        if (!embedQuotes) quotedQueries(event).forEach { query ->
            IconButton(onClick = { onNavigate(query) }) { Icon(Icons.Outlined.FormatQuote, "Open quoted note") }
        }
    }
}

@Composable
private fun EventImage(url: String, compact: Boolean, onOpen: () -> Unit) {
    var failed by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    if (failed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onOpen) { Icon(Icons.Outlined.BrokenImage, "Image unavailable. Open gallery to retry.") }
            IconButton(onClick = { openUrl(context, url) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open image in browser") }
        }
    } else {
        AsyncImage(model = url, contentDescription = "Image attached to this event", onError = { failed = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = if (compact) 280.dp else 600.dp).clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Open image gallery", onClick = onOpen), contentScale = ContentScale.FillWidth)
    }
}
