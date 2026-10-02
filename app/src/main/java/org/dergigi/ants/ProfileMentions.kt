package org.dergigi.ants

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalLoadMentionProfiles = staticCompositionLocalOf<(List<String>) -> Unit> { {} }

/** Resolve visible references immediately rather than waiting for the whole search. */
@Composable
internal fun ResolveMentionProfiles(event: Nip01Event) {
    val pageId = LocalThreadState.current.state.pageId
    val load by rememberUpdatedState(LocalLoadMentionProfiles.current)
    LaunchedEffect(event.id, pageId) {
        val keys = withContext(Dispatchers.Default) {
            (listOf(event.pubkey) + linkedProfileKeys(event)).distinct().take(100)
        }
        load(keys)
    }
}
