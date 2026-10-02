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
