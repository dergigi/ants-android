package org.dergigi.ants

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LinkPreview(val url: String, val title: String, val description: String, val image: String?)

internal fun publicPreviewAddress(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
    val bytes = address.address.map { it.toInt() and 255 }
    if (bytes.size == 16) return bytes[0] and 0xe0 == 0x20
    return bytes[0] != 0 && bytes[0] < 224 && !(bytes[0] == 100 && bytes[1] in 64..127) &&
        !(bytes[0] == 198 && bytes[1] in 18..19)
}

internal fun previewUrl(value: String): HttpUrl? {
    if (value.length > 2048) return null
    val url = value.toHttpUrlOrNull() ?: return null
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.port != 443) return null
    val host = url.host.trimEnd('.').lowercase(Locale.ROOT)
    if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || !host.contains('.') && ':' !in host) return null
    if (':' in host || host.all { it.isDigit() || it == '.' }) {
        if (!runCatching { publicPreviewAddress(InetAddress.getByName(host)) }.getOrDefault(false)) return null
    }
    return url.newBuilder().fragment(null).build()
}

internal fun firstPreviewUrl(content: String, media: List<String>): String? = webLinks(content).asSequence()
    .map { it.text }.filter { it !in media && nostrIdentifierFromUrl(it) == null }
    .mapNotNull(::previewUrl).firstOrNull { url ->
        url.pathSegments.lastOrNull().orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) !in
            setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "svg", "apng", "mp4", "webm", "mov", "m4v", "mp3", "ogg", "wav", "flac", "pdf", "zip", "apk")
    }?.toString()

internal fun parseLinkPreview(bytes: ByteArray, finalUrl: String, charset: String? = null): LinkPreview? {
    val base = previewUrl(finalUrl) ?: return null
    val document = Jsoup.parse(ByteArrayInputStream(bytes), charset, finalUrl)
    val metadata = linkedMapOf<String, String>()
    document.select("meta").forEach { element ->
        val key = element.attr("property").ifBlank { element.attr("name") }.lowercase(Locale.ROOT)
        val value = element.attr("content").trim()
        if (value.isNotEmpty()) metadata.putIfAbsent(key, value)
    }
    fun text(value: String?, max: Int) = value.orEmpty().replace(Regex("\\s+"), " ").trim().take(max)
    val title = text(metadata["og:title"] ?: metadata["twitter:title"] ?: document.title(), 200)
    val description = text(metadata["og:description"] ?: metadata["twitter:description"] ?: metadata["description"], 350)
    val image = listOfNotNull(metadata["og:image:secure_url"], metadata["og:image"], metadata["twitter:image"], metadata["twitter:image:src"])
        .firstNotNullOfOrNull { base.resolve(it)?.toString()?.let(::previewUrl)?.toString() }
    if (title.isBlank() && description.isBlank() && image == null) return null
    return LinkPreview(base.toString(), title.ifBlank { base.host }, description, image)
}

private class PreviewCache<T>(private val limit: Int, private val byteLimit: Int, private val weight: (T) -> Int) {
    data class Entry<T>(val value: T?, val expires: Long)
    private val entries = LinkedHashMap<String, Entry<T>>(16, .75f, true)
    @Synchronized fun get(key: String): Entry<T>? = entries[key]?.takeIf { it.expires > SystemClock.elapsedRealtime() }
    @Synchronized fun put(key: String, value: T?) {
        entries[key] = Entry(value, SystemClock.elapsedRealtime() + if (value == null) 300_000 else 1_800_000)
        while (entries.size > limit || entries.values.sumOf { it.value?.let(weight) ?: 0 } > byteLimit) entries.remove(entries.keys.first())
    }
    @Synchronized fun clear() = entries.clear()
}

internal class LinkPreviewRepository {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
        .dns(object : Dns { override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
            if (addresses.isEmpty() || addresses.any { !publicPreviewAddress(it) }) throw UnknownHostException("Non-public preview host")
        } }).build()
    private val slots = Semaphore(2)
    private val locks = Array(32) { Mutex() }
    private val metadata = PreviewCache<LinkPreview>(128, 1_000_000) { (it.url.length + it.title.length + it.description.length + (it.image?.length ?: 0)) * 2 }
    private val images = PreviewCache<ByteArray>(32, 8 * 1024 * 1024) { it.size }
    private data class Download(val url: String, val bytes: ByteArray, val charset: String?, val redirect: String? = null)

    private suspend fun request(url: HttpUrl, image: Boolean): Download = suspendCancellableCoroutine { continuation ->
        val max = if (image) 2 * 1024 * 1024 else 256 * 1024
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "ants-link-preview/1.0")
            .header("Accept", if (image) "image/*" else "text/html,application/xhtml+xml").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (it.code in setOf(301, 302, 303, 307, 308)) return@use Download(url.toString(), byteArrayOf(), null,
                        it.header("Location") ?: throw IOException("Missing redirect"))
                    if (!it.isSuccessful) throw IOException("Preview unavailable")
                    val body = it.body ?: throw IOException("Empty preview")
                    val type = body.contentType()
                    if (image && type?.type != "image" || !image && type?.subtype !in setOf("html", "xhtml+xml")) throw IOException("Not preview content")
                    val source = body.source()
                    source.request(max.toLong() + 1)
                    if (image && source.buffer.size > max) throw IOException("Preview image too large")
                    Download(url.toString(), source.readByteArray(minOf(source.buffer.size, max.toLong())), type?.charset()?.name())
                } }
                if (!continuation.isCancelled) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
    private suspend fun download(value: String, image: Boolean): Download? = withTimeoutOrNull(8_000) {
        slots.withPermit {
            var url = previewUrl(value) ?: return@withPermit null
            repeat(4) {
                val result = request(url, image)
                if (result.redirect == null) return@withPermit result
                url = url.resolve(result.redirect)?.toString()?.let(::previewUrl) ?: return@withPermit null
            }
            null
        }
    }
    private suspend fun <T> cached(url: String, cache: PreviewCache<T>, load: suspend () -> T?): T? =
        locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            cache.get(url)?.let { return@withLock it.value }
            val value = try { load() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            cache.put(url, value)
            value
        }
    suspend fun preview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        cached(url, metadata) { download(url, false)?.let { parseLinkPreview(it.bytes, it.url, it.charset) } }
    }
    suspend fun image(url: String): ByteArray? = withContext(Dispatchers.IO) {
        cached(url, images) { download(url, true)?.bytes }
    }
    fun clear() { metadata.clear(); images.clear() }
    fun close() { NetworkCleanup.close(client); clear() }
}
