package org.dergigi.ants

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

private val publicNostrIdentifier = Regex(
    "(?<![a-z0-9])(?:npub|nprofile|note|nevent|naddr)1[023456789acdefghjklmnpqrstuvwxyz]+(?![a-z0-9])",
    RegexOption.IGNORE_CASE,
)
private val privateNostrIdentifier = Regex("(?i)\\b(?:nsec1|ncryptsec1)[a-z0-9]*")
private val bareUrlHost = Regex("^[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+(?:[/:?#].*)?$")

internal fun decodeUrlComponent(value: String): String = runCatching {
    // URLDecoder treats '+' as a space, but here we are decoding a URI, not a form body.
    URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
}.getOrDefault(value)

internal fun containsPrivateNostrKey(value: String): Boolean {
    var decoded = value
    repeat(3) {
        if (privateNostrIdentifier.containsMatchIn(decoded)) return true
        decoded = decodeUrlComponent(decoded)
    }
    return false
}

/** Extract locally from a standalone URL, without fetching the site or following redirects. */
internal fun nostrIdentifierFromUrl(value: String): String? {
    val raw = value.trim()
    if (raw.length > 2000 || raw.any { it.isWhitespace() || it == '"' || it == '\'' } || containsPrivateNostrKey(raw)) return null
    val url = when {
        raw.startsWith("https://", true) || raw.startsWith("http://", true) -> raw
        bareUrlHost.matches(raw) -> "https://$raw"
        else -> return null
    }
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.rawAuthority.isNullOrBlank()) return null
    var decoded = url
    repeat(3) {
        for (match in publicNostrIdentifier.findAll(decoded)) {
            val token = match.value
            val lower = token.lowercase(Locale.ROOT)
            val valid = runCatching {
                when {
                    lower.startsWith("npub1") -> Nip19.npubDecode(token)
                    lower.startsWith("nprofile1") -> Nip19.nprofileDecode(token)
                    lower.startsWith("note1") -> Nip19.noteDecode(token)
                    lower.startsWith("nevent1") -> Nip19.neventDecode(token)
                    lower.startsWith("naddr1") -> Nip19.naddrDecode(token)
                }
            }.isSuccess
            if (valid) return lower
        }
        decoded = decodeUrlComponent(decoded)
    }
    return null
}
