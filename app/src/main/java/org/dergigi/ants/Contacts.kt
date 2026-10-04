package org.dergigi.ants

internal const val MAX_SEARCH_CONTACTS = 5000

/** Only public NIP-02 p tags; relay hints and petnames are not identities. */
internal fun contactPubkeys(event: Nip01Event): List<String> = event.tags.asSequence()
    .filter { it.firstOrNull() == "p" }
    .mapNotNull { it.getOrNull(1)?.takeIf { key -> key.matches(Regex("[0-9a-fA-F]{64}")) }?.lowercase() }
    .distinct().toList()

/** NIP-01 replaces by timestamp, choosing the lowest ID in a timestamp tie. */
internal fun latestContactList(events: List<Nip01Event>, owner: String): Nip01Event? = events
    .filter { it.kind == 3 && it.pubkey == owner }
    .sortedWith(compareByDescending<Nip01Event> { it.createdAt }.thenBy { it.id }).firstOrNull()
