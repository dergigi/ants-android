package org.dergigi.ants

internal data class CommandDefinition(val name: String, val description: String)
internal val slashCommands = listOf(
    CommandDefinition("/help", "Commands and search help"),
    CommandDefinition("/examples", "Search examples you can tap"),
    CommandDefinition("/kinds", "Event kind shortcuts"),
    CommandDefinition("/login", "Connect an Android signer"),
    CommandDefinition("/logout", "Disconnect your account"),
    CommandDefinition("/clear", "Clear cached results, profiles, and images"),
    CommandDefinition("/tutorial", "Load the web ants tutorial"),
)

internal const val tutorialPointer = "nevent1qqsqnndhkz4u26m4v4gut2xjsun8hzfxn75spzcr8337a06g66zwzespzamhxue69uhksctkv4hzuer9wfnkjemf9e3k7mgehz685"

// Web ants replacements.txt kind mappings, plus existing Android aliases.
internal val kindAliases = linkedMapOf(
    "profile" to listOf(0), "tweet" to listOf(1), "note" to listOf(1), "notes" to listOf(1),
    "repost" to listOf(6), "reaction" to listOf(7), "image" to listOf(20), "picture" to listOf(20),
    "video" to listOf(21, 22), "media" to listOf(20, 21, 22), "file" to listOf(1063),
    "code" to listOf(1337), "patch" to listOf(1617), "issue" to listOf(1621), "report" to listOf(1984),
    "nutzap" to listOf(9321), "zap" to listOf(9735), "highlight" to listOf(9802),
    "muted" to listOf(10000), "pin" to listOf(10001), "bookmark" to listOf(10003),
    "blogpost" to listOf(30023), "article" to listOf(30023), "longform" to listOf(30023),
    "followpack" to listOf(39089),
)

internal data class SearchExample(val query: String, val description: String, val section: String, val needsLogin: Boolean = false)
internal val searchExamples = listOf(
    SearchExample("vibe coding", "Search text", "Basics"),
    SearchExample("bitcoin OR lightning", "Either topic", "Basics"),
    SearchExample("\"proof of work\"", "Ask relays for a phrase", "Basics"),
    SearchExample("#asknostr", "Find a hashtag", "Basics"),
    SearchExample("#photography", "Explore photographs", "Basics"),
    SearchExample("#SovEng", "Sovereign engineering", "Basics"),
    SearchExample("#pugstr OR #horsestr OR #goatstr", "Several hashtags", "Basics"),
    SearchExample("by:dergigi", "One author's events", "People"),
    SearchExample("by:fiatjaf", "One author's events", "People"),
    SearchExample("by:@dergigi.com", "Resolve a NIP-05 address", "People"),
    SearchExample("GM by:dergigi", "Combine text and author", "People"),
    SearchExample("#YESTR by:dergigi", "Combine hashtag and author", "People"),
    SearchExample("knowledge by:platobot@dergigi.com", "Text with a NIP-05 author", "People"),
    SearchExample("p:fiatjaf", "Search profile metadata", "People"),
    SearchExample("p:RSS", "Find feed accounts", "People"),
    SearchExample("by:@me", "Your events", "Your account", true),
    SearchExample("GM by:@me", "Your GM notes", "Your account", true),
    SearchExample("mentions:@me", "Events tagging you", "Your account", true),
    SearchExample("by:@me has:image", "Your images", "Your account", true),
    SearchExample("has:image", "Image links in events", "Media"),
    SearchExample("Bitcoin has:image", "Images about Bitcoin", "Media"),
    SearchExample("by:dergigi has:image", "Images from an author", "Media"),
    SearchExample("has:video", "Video links in events", "Media"),
    SearchExample("is:image", "Picture events", "Media"),
    SearchExample("is:video", "Both video event kinds", "Media"),
    SearchExample("is:media", "Picture and video events", "Media"),
    SearchExample("site:yt", "YouTube mentions", "Links"),
    SearchExample("site:github.com", "GitHub mentions", "Links"),
    SearchExample("https://dergigi.com/vew", "Find a source URL", "Links"),
    SearchExample("is:highlight", "Highlighted passages", "Kinds"),
    SearchExample("is:highlight \"proof of work\"", "Search highlights", "Kinds"),
    SearchExample("is:reaction by:dergigi", "Discover reacted-to posts", "Kinds"),
    SearchExample("is:article bitcoin", "Long-form articles", "Kinds"),
    SearchExample("is:code", "Code snippets", "Kinds"),
    SearchExample("is:followpack art", "Follow packs", "Kinds"),
    SearchExample("kind:0 OR kind:1", "Numeric kinds", "Kinds"),
    SearchExample("hello since:2021-01-01 until:2021-12-31", "A full date range (UTC)", "Dates"),
    SearchExample("GM by:dergigi since:2024-01-01 until:2024-03-31", "An author's events in a date range", "Dates"),
)
