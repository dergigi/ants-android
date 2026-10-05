package org.dergigi.ants

import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.util.Locale

internal data class FileAttachment(
    val title: String,
    val description: String,
    val urls: List<String>,
    val mime: String?,
    val size: Long?,
    val hash: String?,
    val image: String?,
    val video: String?,
    val poster: String?,
)

/** Tags take precedence; GIF indexers also put display metadata inside a JSON content object. */
internal fun fileAttachment(event: Nip01Event): FileAttachment {
    val content = event.content.trim()
    val structured = content.startsWith('{') || content.startsWith('[')
    val json = if (structured && content.length <= 65_536) runCatching {
        val tokener = JSONTokener(content)
        val obj = tokener.nextValue() as? JSONObject
        obj?.takeIf { tokener.nextClean().code == 0 }
    }.getOrNull() else null
    fun field(name: String) = (json?.opt(name) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    fun tag(name: String) = event.tagValue(name)?.trim()?.takeIf { it.isNotEmpty() }
    val taggedUrls = event.tags.filter { it.firstOrNull() == "url" }.mapNotNull { it.getOrNull(1)?.let(::attachmentUrl) }
    val fallbackUrls = event.tags.filter { it.firstOrNull() == "fallback" }.mapNotNull { it.getOrNull(1)?.let(::attachmentUrl) }
    val urls = (taggedUrls + listOfNotNull(field("sourceUrl")?.let(::attachmentUrl), field("url")?.let(::attachmentUrl)) + fallbackUrls).distinct().take(10)
    val primary = urls.firstOrNull()
    val mime = (tag("m") ?: field("mimeType"))?.lowercase(Locale.ROOT)?.take(100)
    val size = tag("size")?.toLongOrNull()?.takeIf { it >= 0 }
        ?: (json?.opt("size")?.toString()?.toLongOrNull()?.takeIf { it >= 0 })
    val filename = primary?.let { runCatching { URI(it).path.substringAfterLast('/') }.getOrNull() }?.takeIf { it.isNotBlank() }
    val title = (tag("title") ?: field("title") ?: tag("name") ?: filename ?: "File").take(200)
    val description = (if (structured) field("description").orEmpty() else content).take(4000)
        .takeUnless { it == title || it in urls }.orEmpty()
    val secure = primary?.takeIf { URI(it).scheme == "https" }
    val extension = filename?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    val image = secure?.takeIf { mime?.startsWith("image/") == true || extension in setOf("gif", "jpg", "jpeg", "png", "webp", "avif", "apng", "svg") }
    val video = secure?.takeIf { image == null && (mime?.startsWith("video/") == true || extension in setOf("mp4", "webm", "mov", "m4v", "mkv")) }
    val poster = (tag("thumb") ?: tag("image"))?.let(::attachmentUrl)?.takeIf { URI(it).scheme == "https" }
    return FileAttachment(title, description, urls, mime, size, tag("x")?.take(128), image, video, poster)
}
