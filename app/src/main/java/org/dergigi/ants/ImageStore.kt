package org.dergigi.ants

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

// Adapted from Boris's ImageStore. Keep binary sharing separate from URL sharing.
internal object ImageStore {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).followSslRedirects(false).build()
    private const val maxBytes = 50L * 1024 * 1024
    private data class Download(val file: File, val mime: String, val name: String)

    private fun fetch(context: Context, url: String): Download {
        require(Uri.parse(url).scheme == "https") { "Only HTTPS images are supported." }
        val folder = File(context.cacheDir, "shared-images").apply { mkdirs() }
        // Give receiving apps a day to read shared files; don't delete a fresh share.
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Image download failed (${response.code}).")
            val body = response.body ?: throw IOException("Empty image response.")
            if (body.contentLength() > maxBytes) throw IOException("Image exceeds 50 MB.")
            val extension = Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
            val fallback = when(extension) { "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; "avif" -> "image/avif"; else -> null }
            val header = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
            val mime = if (header.startsWith("image/")) header else if (header.isEmpty() || header == "application/octet-stream") fallback else null
            if (mime == null) throw IOException("The server did not return an image.")
            val suffix = when(mime) { "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; "image/avif" -> "avif"; "image/svg+xml" -> "svg"; else -> "jpg" }
            val name = "ants-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.$suffix"
            val file = File(folder, name)
            try {
                body.byteStream().use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val size = input.read(buffer); if (size < 0) break
                        total += size; if (total > maxBytes) throw IOException("Image exceeds 50 MB.")
                        output.write(buffer, 0, size)
                    }
                    if (total == 0L) throw IOException("Empty image response.")
                } }
                Download(file, mime, name)
            } catch (e: Exception) { file.delete(); throw e }
        }
    }

    fun save(context: Context, url: String) {
        val image = fetch(context, url)
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, image.name)
                put(MediaStore.Images.Media.MIME_TYPE, image.mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ants")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create image in Pictures.")
            try {
                resolver.openOutputStream(uri)?.use { output -> image.file.inputStream().use { it.copyTo(output) } } ?: throw IOException("Could not write image.")
                if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
        } finally { image.file.delete() }
    }

    fun share(context: Context, url: String): Intent {
        val image = fetch(context, url)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image.file)
        return Intent(Intent.ACTION_SEND).apply {
            type = image.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
