package org.dergigi.ants

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

private val encryptedKinds = setOf(4, 44, 1059, 24133, 23194, 23195)

/** Recognize opaque payloads, not prose that merely mentions encryption or includes a code sample. */
internal fun Nip01Event.hasEncryptedContent(): Boolean {
    if (kind in encryptedKinds) return true
    if (kind != 1) return false
    val payload = content.trim()
    if (payload.length < 128 || payload.length % 4 != 0) return false
    if (payload.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it !in "+/=" }) return false
    val bytes = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return false
    // Plain text encoded as Base64 is not sufficient evidence of an encrypted note.
    return runCatching {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
    }.isFailure
}
