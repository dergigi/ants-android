package org.dergigi.ants

import android.content.Context
import java.security.MessageDigest

internal data class LanguagePrediction(val code: String, val confidence: Double)

/** One bundled, checksum-verified model. Input crosses JNI as real UTF-8, not modified JNI UTF-8. */
internal class FastTextLanguage(context: Context) {
    private val assets = context.applicationContext.assets
    private val ready by lazy {
        try {
            System.loadLibrary("ants_language")
            val model = assets.open("lid.176.ftz").use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }
            hash == "8f3472cfe8738a7b6099e8e999c3cbfae0dcd15696aac7d7738a8039db603e83" && nativeLoad(model)
        } catch (_: Exception) { false } catch (_: UnsatisfiedLinkError) { false }
    }
    fun predict(text: String): List<LanguagePrediction> {
        if (!ready) return emptyList()
        return nativePredict(text.toByteArray(Charsets.UTF_8)).orEmpty().lineSequence().mapNotNull { row ->
            val fields = row.split('\t')
            val score = fields.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            fields.first().takeIf { it.matches(Regex("[a-z]{2,3}")) && score.isFinite() }
                ?.let { LanguagePrediction(it, score.coerceIn(0.0, 1.0)) }
        }.toList()
    }
    private external fun nativeLoad(model: ByteArray): Boolean
    private external fun nativePredict(text: ByteArray): String?
}
