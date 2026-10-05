package org.dergigi.ants

import android.net.Uri
import java.util.Locale

internal enum class ResultFilterMode(val label: String) { ALWAYS("Always"), SMART("Smart"), NEVER("Never") }

internal data class ContentFilterSettings(
    val mode: ResultFilterMode = ResultFilterMode.SMART,
    val maxEmojis: Int? = 3,
    val maxHashtags: Int? = 3,
    val maxMentions: Int? = 6,
    val hideEncrypted: Boolean = true,
    val hideLinks: Boolean = false,
    val hideBridged: Boolean = true,
    val hideBots: Boolean = false,
    val hideNsfw: Boolean = false,
    val verifiedOnly: Boolean = false,
    val fuzzyEnabled: Boolean = true,
    val resultFilter: String = "",
) {
    fun enabled(count: Int): Boolean = mode == ResultFilterMode.ALWAYS || mode == ResultFilterMode.SMART && count >= 69
    fun emojiAutoDisabled(query: String): Boolean = mode == ResultFilterMode.SMART && ContentAnalysis.countEmojis(query) >= 2

    companion object {
        fun cleared() = ContentFilterSettings(mode = ResultFilterMode.NEVER, maxEmojis = null, maxHashtags = null,
            maxMentions = null, hideEncrypted = false, hideBridged = false, fuzzyEnabled = false)
    }
}

internal data class ContentFacts(val emojis: Int, val hashtags: Int, val mentions: Int, val links: Boolean, val nsfw: Boolean)

/** Port of ants contentAnalysis.ts and its URL/NIP-19 helpers; see docs/RESULT_FILTERS.md. */
internal object ContentAnalysis {
    private val emoji by lazy {
        val source = checkNotNull(javaClass.getResourceAsStream("/emoji-regex-9.2.2.txt"))
            .bufferedReader().use { it.readText().trim() }
        Regex(source)
    }
    private val hashtags = Regex("#[A-Za-z0-9_]+")
    private val usernames = Regex("@[A-Za-z0-9_]+")
    private val identifiers = Regex("(^|[^0-9a-z])((?:npub|nsec|note|nprofile|nevent|nrelay|naddr)1[023456789acdefghjklmnpqrstuvwxyz]+)(?=$|[^0-9a-z])", RegexOption.IGNORE_CASE)
    private val urls = Regex("(https?://[^\\s'\"<>]+)(?!\\w)", RegexOption.IGNORE_CASE)
    private val media = Regex("\\.(png|jpe?g|gif|gifs|apng|webp|avif|svg|m4a|mp3|wav|flac|aac|opus|mp4|webm|ogg|ogv|mov|m4v)$", RegexOption.IGNORE_CASE)
    private val botWords = Regex("(^|[^A-Za-z0-9_])(bot|automated|autopost)(?=$|[^A-Za-z0-9_])", RegexOption.IGNORE_CASE)
    private val bridgeWords = listOf("mostr.pub", "mastodon", "bluesky", "bsky.app", "bsky.social")

    fun countEmojis(text: String): Int = emoji.findAll(text).count()

    fun countMentions(text: String): Int {
        val sources = linkedSetOf(text.trim(), Uri.decode(text.trim()))
        // The web helper also examines decoded URL components, deduplicating NIP-19 identifiers.
        runCatching {
            val uri = Uri.parse(if (text.contains("://")) text.trim() else "https://${text.trim()}")
            sources.add(uri.toString())
            uri.encodedPath?.let(sources::add)
            uri.encodedFragment?.let(sources::add)
            uri.queryParameterNames.forEach { key -> uri.getQueryParameters(key).forEach { value ->
                sources.add(value); sources.add(Uri.decode(value))
            } }
        }
        val keys = mutableSetOf<String>()
        sources.forEach { source ->
            listOf(source, Uri.decode(source)).forEach { decoded ->
                identifiers.findAll(decoded).forEach { keys.add(it.groupValues[2].lowercase(Locale.ROOT)) }
            }
        }
        return usernames.findAll(text).count() + keys.size
    }

    fun containsLink(text: String): Boolean = urls.findAll(text).any { match ->
        val isMedia = runCatching {
            val uri = Uri.parse(match.value)
            val filename = uri.getQueryParameter("filename")?.takeIf(String::isNotEmpty)
                ?: uri.getQueryParameter("name")?.takeIf(String::isNotEmpty) ?: uri.encodedPath.orEmpty()
            !uri.host.isNullOrBlank() && media.containsMatchIn(filename)
        }.getOrDefault(false)
        !isMedia
    }

    fun facts(content: String): ContentFacts = ContentFacts(countEmojis(content), hashtags.findAll(content).count(),
        countMentions(content), containsLink(content), content.contains("nsfw", true) || content.contains("nude", true))

    fun hasBotHint(about: String): Boolean = botWords.containsMatchIn(about)
    fun isBot(profile: Profile?): Boolean = profile?.bot == true || profile?.let { hasBotHint(it.about) } == true
    fun isBridged(profile: Profile?): Boolean = profile != null && bridgeWords.any { profile.nip05.contains(it, true) }

    fun accepts(facts: ContentFacts, profile: Profile?, settings: ContentFilterSettings, emojiDisabled: Boolean, verified: Boolean): Boolean {
        if (!emojiDisabled && settings.maxEmojis?.let { facts.emojis > it } == true) return false
        if (settings.maxHashtags?.let { facts.hashtags > it } == true) return false
        if (settings.maxMentions?.let { facts.mentions > it } == true) return false
        if (settings.hideLinks && facts.links || settings.hideNsfw && facts.nsfw) return false
        if (settings.hideBridged && isBridged(profile) || settings.hideBots && isBot(profile)) return false
        return !settings.verifiedOnly || verified
    }
}
