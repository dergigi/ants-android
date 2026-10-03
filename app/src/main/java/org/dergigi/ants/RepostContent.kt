package org.dergigi.ants

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun taggedNoteReferences(event: Nip01Event): List<QuoteReference> = event.tags.mapNotNull { tag ->
    val type = tag.firstOrNull()
    if (type != "e" && type != "a") return@mapNotNull null
    val query = tag.getOrNull(1)?.let { referenceQuery(type, it) } ?: return@mapNotNull null
    quoteReference(query)?.let { it.copy(relays = listOfNotNull(tag.getOrNull(2))) }
}.distinctBy { it.key }

@Composable
internal fun RepostContent(event: Nip01Event, onNavigate: (String) -> Unit) {
    val ancestors = LocalQuoteAncestors.current + event.id
    val reference = remember(event.id) { taggedNoteReferences(event).firstOrNull() } ?: return
    val original by produceState<Nip01Event?>(null, event.id) {
        value = withContext(Dispatchers.Default) {
            runCatching { Nip01Event.parse(JSONObject(event.content)) }.getOrNull()?.takeIf {
                it.kind == 1 && it.id !in ancestors && reference.matches(it) && it.verify() && it.isRenderable()
            }
        }
    }
    val loadProfiles = LocalLoadMentionProfiles.current
    LaunchedEffect(original?.id) { original?.let { loadProfiles(listOf(it.pubkey)) } }
    if (reference.key in ancestors || ancestors.size > 2) {
        TextButton(onClick = { onNavigate(reference.query) }) { Text("Open original note") }
    } else EmbeddedNote(reference, ancestors, onNavigate, initialEvent = original, label = "Reposted note")
}
