package org.dergigi.ants

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val model: SearchModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receive(intent)
        setContent { AntsApp(model) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent) {
        val raw = if (intent.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else intent.dataString
        raw?.takeIf { it.isNotBlank() }?.let { model.search(incomingQuery(it)) }
    }
}

private val blue = Color(0xFF60A5FA)
private val background = Color(0xFF1A1A1A)
private val card = Color(0xFF2D2D2D)
private val muted = Color(0xFF9CA3AF)
private val examples = listOf("bitcoin" to "Search the nostrverse", "#asknostr" to "Follow a hashtag", "is:highlight" to "Find passages worth keeping", "GM by:dergigi" to "Search someone's notes", "p:fiatjaf" to "Discover people", "nostr has:image" to "Find images")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntsApp(model: SearchModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.detail
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val listState = remember(state.pageId) { LazyListState(state.scrollIndex, state.scrollOffset) }
    LaunchedEffect(listState) {
        val pageId = state.pageId
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) -> model.rememberScroll(pageId, index, offset) }
    }
    LaunchedEffect(listState) {
        val pageId = state.pageId
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) model.pauseFollowing(pageId)
        }
    }
    LaunchedEffect(listState) {
        val pageId = state.pageId
        snapshotFlow { listState.isScrollInProgress && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) }
            .collectLatest { scrollingAway -> if (scrollingAway) model.pauseFollowing(pageId) }
    }
    // requestScrollToItem overrides LazyColumn's key anchoring before the next
    // measure, so arriving results cannot silently push the top out of view.
    var pinnedHead by remember(state.pageId) { mutableStateOf<String?>(null) }
    SideEffect {
        val head = state.events.firstOrNull()?.id
        if (state.searched && state.followingNewest && head != pinnedHead && !listState.isScrollInProgress) {
            listState.requestScrollToItem(0)
            pinnedHead = head
        }
    }
    val focus = LocalFocusManager.current
    fun home() {
        keyboard?.hide()
        focus.clearFocus()
        model.home()
    }
    fun back() { keyboard?.hide(); focus.clearFocus(); model.back() }
    BackHandler(enabled = (state.searched || state.backDepth > 0) && dialog == null && selected == null) { back() }
    fun search(value: String = state.query) {
        keyboard?.hide(); focus.clearFocus()
        model.rememberScroll(state.pageId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        model.search(value)
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = blue, background = background, surface = background, surfaceVariant = card, onSurfaceVariant = muted)) {
        Scaffold(topBar = {
            TopAppBar(navigationIcon = {
                if (state.backDepth > 0) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous search") }
            }, title = { Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Go to home", onClick = { home() }).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ant), null, Modifier.size(36.dp))
                Spacer(Modifier.width(9.dp)); Text("ants", fontWeight = FontWeight.Bold, fontSize = 23.sp, fontFamily = FontFamily.Monospace)
            } }, actions = {
                IconButton(onClick = { dialog = "saved" }) { Icon(Icons.Outlined.Bookmarks, "Saved searches") }
                IconButton(onClick = { dialog = "help" }) { Icon(Icons.Outlined.HelpOutline, "Search help") }
                IconButton(onClick = { dialog = "relays" }) { Icon(Icons.Outlined.Settings, "Relay settings") }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                OutlinedTextField(value = state.query, onValueChange = model::edit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search anything on Nostr", fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { Row {
                        if (state.query.isNotEmpty()) IconButton(onClick = { model.edit("") }) { Icon(Icons.Outlined.Close, "Clear query") }
                        IconButton(onClick = { search() }, enabled = state.query.isNotBlank()) { Icon(Icons.Outlined.ArrowForward, "Search", tint = blue) }
                    } },
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                if (state.searched) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.loading) "Searching… ${state.events.size} results" else "${state.events.size} results · newest first", Modifier.weight(1f), color = muted, style = MaterialTheme.typography.labelMedium)
                        if (state.pendingEvents.isNotEmpty()) {
                            ActionIcon(Icons.Outlined.VerticalAlignTop, "${state.pendingEvents.size} new results · jump to newest", { model.followNewest(); listState.requestScrollToItem(0) }, selected = true)
                        }
                        IconButton(onClick = { model.toggleSaved(state.submitted) }) { Icon(if (state.submitted in state.saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd, "Save search", tint = blue) }
                        if (state.loading) ActionIcon(Icons.Outlined.Stop, "Stop search", model::stop)
                        else IconButton(onClick = { search(state.submitted) }) { Icon(Icons.Outlined.Refresh, "Retry search") }
                    }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.searched) {
                        item { Column(Modifier.padding(top = 24.dp, bottom = 18.dp)) {
                            Text("Follow your curiosity.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text("Advanced Nostr text search.\nFind notes, people, and rabbit holes.", color = muted, style = MaterialTheme.typography.bodyLarge)
                        } }
                        items(examples) { (query, label) ->
                            Surface(onClick = { search(query) }, shape = RoundedCornerShape(8.dp), color = card) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(query, color = blue, fontFamily = FontFamily.Monospace); Spacer(Modifier.height(4.dp)); Text(label, color = muted, style = MaterialTheme.typography.bodySmall) }
                                    Icon(Icons.Outlined.NorthEast, null, tint = muted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (state.history.isNotEmpty()) {
                            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("RECENT SEARCHES", Modifier.weight(1f), color = muted, style = MaterialTheme.typography.labelSmall); ActionIcon(Icons.Outlined.DeleteOutline, "Clear recent searches", model::clearHistory) } }
                            items(state.history) { q -> Text(q, Modifier.fillMaxWidth().clickable { search(q) }.padding(12.dp), color = blue) }
                        }
                        item { Text("No account needed. Stay curious.\nv${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(vertical = 16.dp), color = muted, style = MaterialTheme.typography.bodySmall) }
                    }
                    state.error?.let { error -> item { MessageCard("Couldn't search", error) } }
                    if (state.searched && !state.loading && state.events.isEmpty() && state.error == null) {
                        item { MessageCard("No results yet", "Try fewer filters, another keyword, or different search relays. Relay coverage varies.") }
                    }
                    items(state.events, key = { it.id }) { event -> EventCard(event, state.profiles[event.pubkey], state.profiles, onNavigate = { search(it) }, onOpen = { model.openDetail(event) }, onAuthor = { search("by:${Nip19.npubEncode(event.pubkey)}") }) }
                    if (state.searched && state.statuses.isNotEmpty()) item {
                        TextButton(onClick = { dialog = "relays" }) { Text("${state.statuses.values.count { it == "Complete" }} / ${state.statuses.size} relays completed · relay details") }
                    }
                    if (state.events.size >= 500) item { Text("Showing the first 500 matches. Narrow your search with since: / until:.", color = muted) }
                }
            }
        }
        when (dialog) {
            "help" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Search help") }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Searches go directly to Nostr relays. Text matching depends on each relay's search index.")
                    Text("bitcoin lightning\n\"exact phrase\"\n#asknostr\nby:dergigi\nby:name@example.com\nby:npub1…\nmentions:npub1…\np:alice\nis:note / article / highlight / code\nkind:30023\nsince:2026-01-01\nuntil:2026-10-02\nhas:image / video\nsite:github.com\nbitcoin OR lightning", fontFamily = FontFamily.Monospace, color = blue, fontSize = 13.sp)
                    Text("Paste npub, nprofile, note, nevent, naddr, or an event's hex ID for direct lookup. Share text or ants.sh links to ants from other apps.")
                    Text("MVP: read-only; no login, posting, zaps, grouped boolean expressions, or reverse image search. Images and video are filtered from returned candidates. Results are capped at 500, with up to 100 requested per query per relay.", color = muted)
                    TextButton(onClick = { openUrl(context, "https://github.com/dergigi/ants-android") }) { Text("Source · v${BuildConfig.VERSION_NAME}") }
                }
            }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Got it") } })
            "saved" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Saved searches") }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.saved.isEmpty()) Text("Tap the bookmark beside your results to save a search on this device.")
                    state.saved.forEach { q -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, Modifier.weight(1f).clickable { dialog = null; search(q) }.padding(vertical = 14.dp), color = blue)
                        IconButton(onClick = { model.toggleSaved(q) }) { Icon(Icons.Outlined.DeleteOutline, "Remove saved search") }
                    } }
                }
            }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } })
            "relays" -> RelayDialog(state, model, onDismiss = { dialog = null })
        }
        selected?.let { event ->
            ModalBottomSheet(onDismissRequest = model::dismissDetail) {
                EventDetails(event, state.profiles[event.pubkey], state.profiles, onNavigate = { search(it) }, onAuthor = { search("by:${Nip19.npubEncode(event.pubkey)}") }, raw = state.detailRaw, initialScroll = state.detailScroll, onScroll = { model.rememberDetailScroll(state.pageId, event.id, it) }, onToggleRaw = model::toggleDetailRaw)
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String) {
    Surface(color = card, shape = RoundedCornerShape(8.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text(message, color = muted)
    } }
}

private fun kindLabel(kind: Int) = when (kind) { 0 -> "Profile"; 1 -> "Note"; 30023 -> "Article"; 9802 -> "Highlight"; 1337 -> "Code"; 20 -> "Picture"; 21, 22 -> "Video"; 7 -> "Reaction"; 6 -> "Repost"; 9735 -> "Zap"; else -> "Kind $kind" }
private fun dateLabel(time: Long) = runCatching { DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(time)) }.getOrDefault("")
private fun displayContent(event: Nip01Event, profile: Profile?) = if (event.kind == 0) profile?.about ?: event.content else event.content
private fun eventUrl(event: Nip01Event) = "https://ants.sh/e/${Nip19.noteEncode(event.id)}"

private fun kindIcon(kind: Int): ImageVector = when (kind) {
    9802 -> Icons.Outlined.BorderColor
    30023 -> Icons.Outlined.Article
    0 -> Icons.Outlined.PersonOutline
    1337 -> Icons.Outlined.Code
    20 -> Icons.Outlined.Image
    21, 22 -> Icons.Outlined.Videocam
    7 -> Icons.Outlined.FavoriteBorder
    6 -> Icons.Outlined.Repeat
    9735 -> Icons.Outlined.Bolt
    else -> Icons.Outlined.ChatBubbleOutline
}

private fun relativeTime(timestamp: Long): String {
    val seconds = (System.currentTimeMillis() / 1000 - timestamp).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        seconds < 604800 -> "${seconds / 86400}d"
        else -> runCatching { DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(timestamp)) }.getOrDefault("")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick) { Icon(icon, label, Modifier.size(19.dp), tint = if (selected) blue else muted) }
    }
}

@Composable
private fun EventCard(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, onNavigate: (String) -> Unit, onOpen: () -> Unit, onAuthor: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(onClick = onOpen, shape = RoundedCornerShape(8.dp), color = card, border = BorderStroke(1.dp, Color(0xFF3D3D3D))) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF353535)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(event.kind), kindLabel(event.kind), Modifier.size(16.dp), tint = muted)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Dns, "Nostr event", Modifier.size(14.dp), tint = muted)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (event.kind != 9802) event.tagValue("title")?.takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (event.kind == 9802) HighlightContent(event, profiles, compact = true, onNavigate = onNavigate)
                else EventContent(event, profile, compact = true, onNavigate = onNavigate)
            }
            HorizontalDivider(color = Color(0xFF3D3D3D))
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onAuthor).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile, event.pubkey, onAuthor, size = 22)
                    Spacer(Modifier.width(7.dp))
                    Text(profile?.name ?: Nip19.npubEncode(event.pubkey).let { it.take(10) + "…" }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(relativeTime(event.createdAt), color = muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                ActionIcon(Icons.Outlined.ContentCopy, "Copy event ID", { clipboard.setText(AnnotatedString("nostr:${Nip19.noteEncode(event.id)}")) })
                ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in browser", { openUrl(context, "https://njump.me/${Nip19.noteEncode(event.id)}") })
                ActionIcon(Icons.Outlined.MoreHoriz, "Event details and actions", onOpen)
            }
        }
    }
}

@Composable
private fun Avatar(profile: Profile?, pubkey: String, onClick: () -> Unit, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(blue.copy(alpha = 0.15f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text((profile?.name ?: pubkey).take(1).uppercase(), color = blue, fontWeight = FontWeight.Bold)
        profile?.picture?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun EventDetails(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, onNavigate: (String) -> Unit, onAuthor: () -> Unit, raw: Boolean, initialScroll: Int, onScroll: (Int) -> Unit, onToggleRaw: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scrollState = remember(event.id) { ScrollState(initialScroll) }
    val saveScroll by rememberUpdatedState(onScroll)
    LaunchedEffect(scrollState) { snapshotFlow { scrollState.value }.collectLatest { saveScroll(it) } }
    Column(Modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile, event.pubkey, onAuthor); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onAuthor)) { Text(profile?.name ?: Nip19.npubEncode(event.pubkey).take(24) + "…", fontWeight = FontWeight.Bold); Text("${kindLabel(event.kind)} · ${dateLabel(event.createdAt)}", color = muted, style = MaterialTheme.typography.bodySmall) }
        }
        event.tagValue("title")?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        SelectionContainer {
            when {
                raw -> Text(event.toJsonString(), fontFamily = FontFamily.Monospace)
                event.kind == 9802 -> HighlightContent(event, profiles, compact = false, onNavigate = onNavigate)
                else -> EventContent(event, profile, compact = false, onNavigate = onNavigate)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in browser", { openUrl(context, "https://njump.me/${Nip19.noteEncode(event.id)}") })
            ActionIcon(Icons.Outlined.Share, "Share event", { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, eventUrl(event)) }, "Share event")) })
            ActionIcon(Icons.Outlined.ContentCopy, if (raw) "Copy event JSON" else "Copy event ID", { clipboard.setText(AnnotatedString(if (raw) event.toJsonString() else "nostr:${Nip19.noteEncode(event.id)}")) })
            ActionIcon(Icons.Outlined.DataObject, if (raw) "Show rendered event" else "Show raw event JSON", onToggleRaw, selected = raw)
        }
    }
}

@Composable
private fun RelayDialog(state: SearchState, model: SearchModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(state.relays.joinToString("\n")) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Search relays") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Queries are sent to these relays. Choose relays that support Nostr text search (NIP-50).", color = muted)
            OutlinedTextField(text, { text = it; error = null }, Modifier.fillMaxWidth(), label = { Text("One wss:// URL per line") }, minLines = 5, isError = error != null, textStyle = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { text = defaultSearchRelays.joinToString("\n") }) { Text("Restore defaults") }
            if (state.statuses.isNotEmpty()) Text("LAST SEARCH", style = MaterialTheme.typography.labelSmall, color = muted)
            state.statuses.forEach { (url, status) -> Column { Text(url.removePrefix("wss://"), fontSize = 13.sp); Text(status, color = if (status == "Complete") blue else muted, fontSize = 12.sp) } }
            Text("Direct lookups also use Damus, nos.lol, and Primal. Public profile names and avatars are fetched from purplepag.es and Damus. Searches and bookmarks stay on this device; queries are visible to relays and image requests go to their hosts.", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }, confirmButton = { TextButton(onClick = { error = model.setRelays(text); if (error == null) onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { Toast.makeText(context, "No app available to open this link.", Toast.LENGTH_SHORT).show() }
}
