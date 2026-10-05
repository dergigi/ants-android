package org.dergigi.ants

import org.junit.Assert.*
import org.junit.Test

class ContentFiltersTest {
    private val empty = ContentFacts(0, 0, 0, false, false)
    private fun accepts(facts: ContentFacts, settings: ContentFilterSettings = ContentFilterSettings(),
        profile: Profile? = null, emojiDisabled: Boolean = false, verified: Boolean = false) =
        ContentAnalysis.accepts(facts, profile, settings, emojiDisabled, verified)

    @Test fun smartThresholdAndModes() {
        assertFalse(ContentFilterSettings().enabled(68))
        assertTrue(ContentFilterSettings().enabled(69))
        assertTrue(ContentFilterSettings(mode = ResultFilterMode.ALWAYS).enabled(0))
        assertFalse(ContentFilterSettings(mode = ResultFilterMode.NEVER).enabled(500))
    }

    @Test fun limitsAreStrictlyGreaterAndCanBeDisabled() {
        assertTrue(accepts(empty.copy(emojis = 3, hashtags = 3, mentions = 6)))
        assertFalse(accepts(empty.copy(emojis = 4)))
        assertFalse(accepts(empty.copy(hashtags = 4)))
        assertFalse(accepts(empty.copy(mentions = 7)))
        assertTrue(accepts(empty.copy(hashtags = 20), ContentFilterSettings(maxHashtags = null)))
        assertFalse(accepts(empty.copy(hashtags = 1), ContentFilterSettings(maxHashtags = 0)))
    }

    @Test fun emojiSequencesAndSmartException() {
        assertEquals(3, ContentAnalysis.countEmojis("👨‍👩‍👧‍👦 🇵🇹 👍🏽"))
        assertFalse(ContentFilterSettings().emojiAutoDisabled("hello 👍"))
        assertTrue(ContentFilterSettings().emojiAutoDisabled("👍 🫂"))
        assertFalse(ContentFilterSettings(mode = ResultFilterMode.ALWAYS).emojiAutoDisabled("👍 🫂"))
        assertTrue(accepts(empty.copy(emojis = 8), emojiDisabled = true))
        assertFalse(accepts(empty.copy(emojis = 8, hashtags = 4), emojiDisabled = true))
    }

    @Test fun bridgeAndBotRules() {
        val bridge = Profile("name", "", null, 0, nip05 = "alice@MOSTR.PUB")
        assertFalse(accepts(empty, profile = bridge))
        assertTrue(accepts(empty, ContentFilterSettings(hideBridged = false), bridge))
        val settings = ContentFilterSettings(hideBots = true)
        assertFalse(accepts(empty, settings, Profile("name", "An automated account", null, 0)))
        assertFalse(accepts(empty, settings, Profile("name", "", null, 0, bot = true)))
        assertTrue(accepts(empty, settings, Profile("name", "I study botany", null, 0)))
        assertTrue(accepts(empty, profile = Profile("name", "bot", null, 0)))
    }

    @Test fun optionalFlagsAndVerification() {
        assertTrue(accepts(empty.copy(nsfw = true, links = true)))
        assertFalse(accepts(empty.copy(nsfw = true), ContentFilterSettings(hideNsfw = true)))
        assertFalse(accepts(empty.copy(links = true), ContentFilterSettings(hideLinks = true)))
        assertFalse(accepts(empty, ContentFilterSettings(verifiedOnly = true)))
        assertTrue(accepts(empty, ContentFilterSettings(verifiedOnly = true), verified = true))
    }

    @Test fun fuzzyMatchingIgnoresLocationAndAcceptsTypos() {
        assertNotNull(ResultFuzzyFilter("nostr").score("a long prefix before NOSTR and a suffix"))
        assertNotNull(ResultFuzzyFilter("nostr").score("nostx"))
        assertNull(ResultFuzzyFilter("nostr").score("abcde"))
        assertNotNull(ResultFuzzyFilter("a".repeat(32) + "b".repeat(32)).score("a".repeat(32)))
    }
}
