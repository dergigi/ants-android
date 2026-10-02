package org.dergigi.ants

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
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

internal data class WebLink(val text: String, val start: Int, val end: Int)
internal fun webLinks(text: String): List<WebLink> = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE).findAll(text).map { match ->
    var url = match.value.trimEnd('.', ',', ';', '!', '?')
    for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
        while (url.endsWith(close) && url.count { it == close } > url.count { it == open }) url = url.dropLast(1)
    }
    WebLink(url, match.range.first, match.range.first + url.length)
}.toList()

internal fun eventImages(event: Nip01Event, compact: Boolean): List<String> {
    val declared = event.tags.filter { it.firstOrNull() == "image" }.mapNotNull { it.getOrNull(1) }
    val inline = webLinks(event.content).map { it.text }.filter {
        Uri.parse(it).path.orEmpty().substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "avif", "gif")
    }
    return (declared + inline).filter { Uri.parse(it).scheme == "https" }.distinct().take(if (compact) 4 else 20)
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

@Composable
internal fun EventContent(event: Nip01Event, profile: Profile?, compact: Boolean) {
    val images = remember(event.id, compact) { eventImages(event, compact) }
    val content = if (event.kind == 0) profile?.about ?: event.content else event.content
    val text = remember(content, images) { withoutRenderedImages(content, images) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (text.isNotBlank()) Text(text, maxLines = if (compact) 9 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp), fontFamily = if (event.kind == 1337) FontFamily.Monospace else FontFamily.Default)
        else if (images.isEmpty()) Text("Open event to inspect its tags.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        images.forEach { image -> EventImage(image, compact) }
    }
}

@Composable
private fun EventImage(url: String, compact: Boolean) {
    var failed by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    if (failed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Outlined.BrokenImage, "Image unavailable")
            IconButton(onClick = { openUrl(context, url) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open image in browser") }
        }
    } else {
        AsyncImage(model = url, contentDescription = "Image attached to this event", onError = { failed = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = if (compact) 280.dp else 600.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.FillWidth)
    }
}
