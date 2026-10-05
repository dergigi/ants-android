package org.dergigi.ants

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntsApp(model: SearchModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val appSettings by model.appSettings.state.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) model.appSettings.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val filterSettings by model.resultFilters.settings.collectAsStateWithLifecycle()
    val filteredResults by model.resultFilters.results.collectAsStateWithLifecycle()
    val visibleEvents = if (filteredResults.pageId == state.pageId) filteredResults.events else emptyList()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var queryEditor by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }
    val editorValue = if (queryEditor.text == state.query) queryEditor else TextFieldValue(state.query, TextRange(state.query.length))
    LaunchedEffect(state.query) {
        if (queryEditor.text != state.query) queryEditor = TextFieldValue(state.query, TextRange(state.query.length))
    }
    val keywordSuggestions = remember(editorValue, searchFocused, state.pubkey) {
        if (searchFocused)
            querySuggestions(editorValue.text, editorValue.selection.start, editorValue.selection.end, loggedIn = state.pubkey != null)
        else null
    }
    val suggestingCommands = searchFocused && state.query.trimStart().startsWith("/")
    val centeredHome = !state.searched && !suggestingCommands && state.error == null
    val pullState = rememberPullToRefreshState()
    var pullRefreshPage by remember { mutableStateOf<Long?>(null) }
    val canRefresh = state.searched && !suggestingCommands && (state.command == null || state.command == "tutorial")
    val refreshingFromPull = state.loading && pullRefreshPage == state.pageId
    SideEffect { CrashReporter.onScreen(if (state.detail != null) "Event details" else if (state.command != null) "Command" else if (state.searched) "Search results" else "Home") }
    val selected = state.detail
    val placeholder = rememberSearchPlaceholder(active = state.query.isEmpty() && !state.loading && selected == null && dialog == null, loggedIn = state.pubkey != null)
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var signingActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    val eventSignerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val id = signingActivityId
        signingActivityId = null
        val returnedId = data?.getStringExtra("id")
        model.finishEventSigning(id, data?.getStringExtra("event"), data?.getStringExtra("result") ?: data?.getStringExtra("signature"),
            result.resultCode != Activity.RESULT_OK || data?.getBooleanExtra("rejected", false) == true || (returnedId != null && returnedId != id))
    }
    LaunchedEffect(state.eventSignRequest) {
        state.eventSignRequest?.let { request ->
            model.eventSigningLaunched(request.id)
            signingActivityId = request.id
            try {
                eventSignerLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${request.event.toJsonString()}")).apply {
                    setPackage(request.packageName)
                    putExtra("type", "sign_event")
                    putExtra("current_user", request.event.pubkey)
                    putExtra("id", request.id)
                })
            } catch (_: Exception) {
                signingActivityId = null
                model.finishEventSigning(request.id, null, null, true)
            }
        }
    }
    val signerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val error = when {
            result.resultCode != Activity.RESULT_OK -> "Connection cancelled or signer unavailable. You can try again."
            data?.getBooleanExtra("rejected", false) == true -> "Connection declined in your signer."
            else -> null
        }
        model.finishLogin(data?.getStringExtra("result") ?: data?.getStringExtra("signature"), data?.getStringExtra("package"), data?.getStringExtra("id"), error)
    }
    LaunchedEffect(state.signerRequest) {
        state.signerRequest?.let { id ->
            model.signerRequestLaunched(id)
            try {
                signerLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
                    putExtra("type", "get_public_key")
                    putExtra("id", id)
                    putExtra("permissions", "[]")
                })
            } catch (_: Exception) {
                model.finishLogin(null, null, id, "No Android signer could be opened. Install Amber or another compatible signer, then try again.")
            }
        }
    }
    val suggestionListState = remember(state.query) { LazyListState() }
    val listState = remember(state.pageId) { LazyListState(state.scrollIndex, state.scrollOffset) }
    var controlsHidden by remember(state.pageId) { mutableStateOf(false) }
    val canHideControls by rememberUpdatedState(canRefresh && !searchFocused && selected == null)
    val hideSearchControls = controlsHidden && canHideControls
    LaunchedEffect(canHideControls) { if (!canHideControls) controlsHidden = false }
    val controlsScrollThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val searchControlsScroll = remember(listState, canHideControls, controlsScrollThreshold) { object : NestedScrollConnection {
        private var distance = 0f
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Accumulate deliberate movement toward the next state. Small
            // reversals subtract distance rather than toggling the header.
            if (source == NestedScrollSource.UserInput && canHideControls) {
                val movement = if (controlsHidden) available.y else -available.y
                if (controlsHidden || listState.canScrollForward) {
                    distance = (distance + movement).coerceAtLeast(0f)
                    if (distance >= controlsScrollThreshold) {
                        controlsHidden = !controlsHidden
                        distance = 0f
                    }
                } else distance = 0f
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            // Separate drags must not accumulate tiny accidental reversals.
            distance = 0f
            return Velocity.Zero
        }
    } }

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
    // requestScrollToItem overrides LazyColumn's key anchoring before the next
    // measure, so arriving results cannot silently push the top out of view.
    var pinnedHead by remember(state.pageId) { mutableStateOf<String?>(null) }
    SideEffect {
        val head = visibleEvents.firstOrNull { state.profileFeedAuthor == null || it.kind != 0 }?.id
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
    BackHandler(enabled = suggestingCommands || keywordSuggestions != null) { keyboard?.hide(); focus.clearFocus() }
    fun search(value: String = state.query) {
        keyboard?.hide(); focus.clearFocus()
        model.rememberScroll(state.pageId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        model.search(value.ifEmpty { placeholder.query })
    }
    fun navigateContent(value: String) {
        if (Uri.parse(value).scheme?.lowercase() in listOf("http", "https")) openUrl(context, value)
        else search(value)
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = blue, background = background, surface = background, surfaceVariant = card, onSurfaceVariant = muted)) {
        CrashReportPrompt(CrashReporter.RECIPIENT_HEX)
        GalleryHost(onSearch = { search(it) }) {
        CompositionLocalProvider(LocalLinkPreviews provides (if (appSettings.richPreviews) model.linkPreviews else null), LocalThreadState provides ThreadState(state, model::loadParent), LocalQuoteState provides QuoteState(state, model::loadQuote, model::openDetail), LocalLoadMentionProfiles provides model::loadMentionProfiles) {
        if (selected?.kind == 30023) {
            BackHandler(onBack = model::dismissDetail)
            Scaffold(topBar = {
                TopAppBar(title = { Text(selected.tagValue("title").orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = { IconButton(onClick = model::dismissDetail) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to results") } })
            }) { padding ->
                EventDetails(selected, state.profiles[selected.pubkey], state.profiles,
                    onNavigate = { navigateContent(it) }, onAuthor = { search("p:${Nip19.npubEncode(selected.pubkey)}") },
                    raw = state.detailRaw, initialScroll = state.detailScroll,
                    onScroll = { model.rememberDetailScroll(state.pageId, selected.id, it) }, onToggleRaw = model::toggleDetailRaw,
                    modifier = Modifier.fillMaxSize().padding(padding))
            }
        } else {
        Scaffold(topBar = {
            if (state.searched) AnimatedVisibility(visible = !hideSearchControls) {
            TopAppBar(navigationIcon = {
                if (state.backDepth > 0) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous search") }
            }, title = { if (state.backDepth == 0) Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Go to home", onClick = { home() }).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AntLogo(loggedIn = state.pubkey != null)
                Spacer(Modifier.width(9.dp)); Text("ants", fontWeight = FontWeight.Bold, fontSize = 23.sp, fontFamily = FontFamily.Monospace)
            } }, actions = {
                IconButton(onClick = { search("/history") }) { Icon(Icons.Outlined.History, "Search history") }
                IconButton(onClick = { search("/help") }) { Icon(Icons.Outlined.HelpOutline, "Search help") }
                ActionIcon(Icons.Outlined.Settings, "Settings", { dialog = "relays" })
                AccountMenu(state.pubkey, state.profiles[state.pubkey], onSearch = { search(it) })
            })
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Column(Modifier.fillMaxSize().padding(
                top = if (centeredHome && keywordSuggestions != null) 64.dp else 0.dp,
                bottom = if (centeredHome && keywordSuggestions != null) 40.dp else 0.dp,
            ), verticalArrangement = if (centeredHome) Arrangement.Center else Arrangement.Top) {
                AnimatedVisibility(visible = !hideSearchControls) {
                Column {
                OutlinedTextField(value = editorValue, onValueChange = {
                    queryEditor = it
                    if (it.text != state.query) model.edit(it.text)
                },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).onFocusChanged { searchFocused = it.isFocused },
                    placeholder = { Text(placeholder.query, fontSize = 15.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Row {
                        if (state.query.isNotEmpty()) IconButton(onClick = { model.edit("") }) { Icon(Icons.Outlined.Close, "Clear query") }
                        else if (!state.loading) NextSearchExample(placeholder)
                        IconButton(onClick = { search() }, enabled = state.query.isEmpty() || state.query.isNotBlank()) { Icon(Icons.Outlined.Search, if (state.query.isEmpty()) "Search ${placeholder.query}" else "Search", tint = blue) }
                    } },
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                keywordSuggestions?.let { suggestions ->
                    QuerySuggestionMenu(suggestions) { choice ->
                        val completed = completeQuery(editorValue.text, suggestions, choice)
                        queryEditor = TextFieldValue(completed.text, TextRange(completed.cursor))
                        model.edit(completed.text)
                    }
                }
                val showTranslation = !suggestingCommands && state.translation.isNotBlank() && state.query.trim() == state.submitted
                if (showTranslation || state.loading) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        if (showTranslation) {
                            QueryTranslationView(state.translation, state.pageId, Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        if (state.loading) {
                            val progressColor = if (state.resolvingNip05) Color(0xFFA78BFA) else if (state.resolvingProfiles) Color(0xFFFBBF24) else muted
                            val progressLabel = if (state.resolvingNip05) "Looking up NIP-05" else if (state.resolvingProfiles) "Looking up profiles" else "Searching events"
                            TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = { PlainTooltip { Text(progressLabel) } }, state = rememberTooltipState()) {
                                IconButton(onClick = model::stop, modifier = Modifier.semantics { stateDescription = progressLabel }) {
                                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = 2.dp, color = progressColor)
                                        Icon(Icons.Outlined.Stop, "Stop search", Modifier.size(12.dp), tint = progressColor)
                                    }
                                }
                            }
                        } else if (state.searched && (state.command == null || state.command == "tutorial")) {
                            IconButton(onClick = { search(state.submitted) }) { Icon(Icons.Outlined.Refresh, "Retry search") }
                        }
                    }
                }
                if (!suggestingCommands && state.searched && (state.command == null || state.command == "tutorial") &&
                    (!state.loading || state.events.isNotEmpty() || state.newerResultIds.isNotEmpty())) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val totalCount = state.events.count { state.profileFeedAuthor == null || it.kind != 0 }
                        val visibleCount = visibleEvents.count { state.profileFeedAuthor == null || it.kind != 0 }
                        val count = if (visibleCount == totalCount) "$totalCount" else "$visibleCount / $totalCount"
                        val resultSummary = if (state.profileFeedAuthor != null) "$count events" else "$count results"
                        Text(resultSummary, Modifier.weight(1f), color = muted, style = MaterialTheme.typography.labelMedium)
                        if (state.command == null) ResultFilterButton(filterSettings, state.submitted, state.events.size, visibleEvents.size,
                            languageCounts = if (filteredResults.pageId == state.pageId) filteredResults.languageCounts else emptyMap(),
                            onSearchLanguages = { search(state.submitted) }) {
                            model.resultFilters.update(it)
                            model.followNewest()
                            listState.requestScrollToItem(0)
                        }
                        if (state.events.any { it.kind != 0 }) ResultSortButton(state.newestFirst) {
                            model.toggleSort()
                            listState.requestScrollToItem(0)
                        }
                        val newVisibleCount = visibleEvents.count { it.id in state.newerResultIds }
                        if (newVisibleCount > 0) {
                            ActionIcon(Icons.Outlined.VerticalAlignTop, "$newVisibleCount new results · jump to top", { model.followNewest(); listState.requestScrollToItem(0) }, selected = true)
                        }
                        if (!state.loading && !showTranslation) IconButton(onClick = { search(state.submitted) }) { Icon(Icons.Outlined.Refresh, "Retry search") }
                    }
                }
                }
                }
                if (!centeredHome) {
                Box(Modifier.weight(1f).nestedScroll(searchControlsScroll).pullToRefresh(
                    state = pullState,
                    isRefreshing = refreshingFromPull,
                    enabled = canRefresh && !state.loading,
                    onRefresh = {
                        if (canRefresh && !state.loading) {
                            search(state.submitted)
                            pullRefreshPage = model.state.value.pageId
                        }
                    },
                )) {
                LazyColumn(Modifier.fillMaxSize(), state = if (suggestingCommands) suggestionListState else listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(if (!state.searched || suggestingCommands || state.command != null) 0.dp else 12.dp)) {
                    if (suggestingCommands) {
                        val matches = slashCommands.filter { it.name.startsWith(state.query.trim().lowercase()) }
                        items(matches, key = { "suggestion-${it.name}" }) { command ->
                            CommandRow(command.name) { search(it) }
                        }
                        if (matches.isEmpty()) item { Text("No matching command", color = muted, style = MaterialTheme.typography.bodySmall) }
                    } else {
                    commandItems(state, onSearch = { search(it) }, onConnect = model::requestLogin, onClearHistory = model::clearHistory)
                    state.error?.let { error -> item { if (state.command != null) CommandTerminal { Text(error, color = MaterialTheme.colorScheme.error) } else MessageCard("Couldn't search", error) } }
                    if (state.searched && (state.command == null || state.command == "tutorial") && !state.loading && state.events.isEmpty() && state.error == null) {
                        item { if (state.command == "tutorial") CommandTerminal { Text("Tutorial unavailable") } else MessageCard("No results yet", "Try fewer filters, another keyword, or different search relays. Relay coverage varies.") }
                    }
                    if (state.command == null && state.events.isNotEmpty() && visibleEvents.isEmpty() && filteredResults.pageId == state.pageId) {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("All results hidden", Modifier.weight(1f), color = muted)
                            if (filteredResults.languageHidden > 0) ActionIcon(Icons.Outlined.Translate, "Show all languages", {
                                model.resultFilters.clearLanguages()
                            })
                            ActionIcon(Icons.Outlined.FilterAltOff, "Show all results", { model.resultFilters.update(filterSettings.copy(mode = ResultFilterMode.NEVER)) })
                        } }
                    }
                    items(visibleEvents, key = { it.id }) { event ->
                        if (state.command == "tutorial") CommandTerminal {
                            EventContent(event, state.profiles[event.pubkey], state.profiles, compact = false, onNavigate = { navigateContent(it) })
                        } else EventCard(event, state.profiles[event.pubkey], state.profiles, onNavigate = { navigateContent(it) }, onOpen = { model.openDetail(event) }, onAuthor = { search("p:${Nip19.npubEncode(event.pubkey)}") })
                    }
                    if (state.searched && state.statuses.isNotEmpty()) item {
                        TextButton(onClick = { dialog = "relays" }) { Text("${state.statuses.values.count { it == "Complete" }} / ${state.statuses.size} relays completed · relay details") }
                    }
                    if (RESULT_MEMORY_LIMIT in state.statuses.values) item {
                        Text("Result memory limit reached. Narrow your search to load more.", color = muted)
                    }
                    if (state.events.count { state.profileFeedAuthor == null || it.kind != 0 } >= 500) item { Text("Showing the first 500 matches. Narrow your search with since: / until:.", color = muted) }
                    }
                }
                if (canRefresh) PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = refreshingFromPull, color = if (state.resolvingNip05) Color(0xFFA78BFA) else if (state.resolvingProfiles) Color(0xFFFBBF24) else muted, modifier = Modifier.align(Alignment.TopCenter))
                }
                }
            }
            if (centeredHome) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Open ants profile") } }, state = rememberTooltipState()) {
                    IconButton(onClick = { search("p:ants.sh") }) {
                        AntLogo(loggedIn = state.pubkey != null, description = "Open ants profile")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { search("/history") }) { Icon(Icons.Outlined.History, "Search history", tint = muted) }
                ActionIcon(Icons.Outlined.Settings, "Settings", { dialog = "relays" })
                IconButton(onClick = { search("/help") }) { Icon(Icons.Outlined.HelpOutline, "Search help", tint = muted) }
                AccountMenu(state.pubkey, state.profiles[state.pubkey], onSearch = { search(it) })
            }
            if (centeredHome) Text("v${BuildConfig.VERSION_NAME}",
                Modifier.align(Alignment.BottomCenter).clickable(onClickLabel = "Open GitHub release") {
                    openUrl(context, "https://github.com/dergigi/ants-android/releases/tag/v${BuildConfig.VERSION_NAME}")
                }.padding(16.dp), color = muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        when (dialog) {
            "relays" -> RelayDialog(state, model, onDismiss = { dialog = null })
        }
        selected?.let { event ->
            ModalBottomSheet(onDismissRequest = model::dismissDetail) {
                EventDetails(event, state.profiles[event.pubkey], state.profiles, onNavigate = { navigateContent(it) }, onAuthor = { search("p:${Nip19.npubEncode(event.pubkey)}") }, raw = state.detailRaw, initialScroll = state.detailScroll, onScroll = { model.rememberDetailScroll(state.pageId, event.id, it) }, onToggleRaw = model::toggleDetailRaw)
            }
        }
        }
        }
        }
    }
}

@Composable
private fun AntLogo(loggedIn: Boolean, description: String? = null) {
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Image(painterResource(R.drawable.ant), description, Modifier.size(36.dp), colorFilter = if (loggedIn) null else grayscale)
}

@Composable
private fun MessageCard(title: String, message: String) {
    Surface(color = card, shape = RoundedCornerShape(8.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text(message, color = muted)
    } }
}

private fun kindLabel(kind: Int) = when (kind) { 0 -> "Profile"; 1 -> "Note"; 3 -> "Contacts"; 1063 -> "File"; 30023 -> "Article"; 9802 -> "Highlight"; 1337 -> "Code"; 20 -> "Picture"; 21, 22 -> "Video"; 7 -> "Reaction"; 6 -> "Repost"; 1617 -> "Patch"; 1621 -> "Issue"; 1984 -> "Report"; 9321 -> "Nutzap"; 9735 -> "Zap receipt"; 10000 -> "Mute list"; 10001 -> "Pinned notes"; 10003 -> "Bookmarks"; 39089 -> "Follow pack"; else -> "Kind $kind" }
private fun dateLabel(time: Long) = runCatching { DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(time)) }.getOrDefault("")
private fun displayContent(event: Nip01Event, profile: Profile?) = if (event.kind == 0) profile?.about ?: event.content else event.content
private fun eventUrl(event: Nip01Event) = "https://ants.sh/e/${Nip19.noteEncode(event.id)}"

private fun kindIcon(kind: Int): ImageVector = when (kind) {
    9802 -> Icons.Outlined.BorderColor
    30023 -> Icons.Outlined.Article
    0 -> Icons.Outlined.PersonOutline
    1337, 1617 -> Icons.Outlined.Code
    1621 -> Icons.Outlined.BugReport
    1984 -> Icons.Outlined.Flag
    20 -> Icons.Outlined.Image
    21, 22 -> Icons.Outlined.Videocam
    7 -> Icons.Outlined.FavoriteBorder
    6 -> Icons.Outlined.Repeat
    10000 -> Icons.Outlined.VolumeOff
    10001 -> Icons.Outlined.PushPin
    10003 -> Icons.Outlined.Bookmarks
    3, 39089 -> Icons.Outlined.Group
    1063 -> Icons.Outlined.AttachFile
    9321, 9735 -> Icons.Outlined.Bolt
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
internal fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean = false) {
    Box(Modifier.requiredSize(48.dp), contentAlignment = Alignment.Center) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick) { Icon(icon, label, Modifier.size(20.dp), tint = if (selected) blue else muted) }
    }
    }
}

@Composable
private fun EventCard(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, onNavigate: (String) -> Unit, onOpen: () -> Unit, onAuthor: () -> Unit) {
    if (event.kind == 0) {
        ProfileCard(event, profile, profiles, onNavigate, onOpen)
        return
    }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Surface(onClick = onOpen, shape = RoundedCornerShape(8.dp), color = card, border = BorderStroke(1.dp, Color(0xFF3D3D3D))) {
        Column(Modifier.fillMaxWidth()) {
            ThreadContext(event, onNavigate)
            if (parentEventId(event) == null) Row(Modifier.fillMaxWidth().background(Color(0xFF353535)).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                KindSearchIcon(event.kind, kindIcon(event.kind), muted, onNavigate)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.Dns, "Nostr event", Modifier.size(14.dp), tint = muted)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (event.kind !in listOf(9802, 30023, 1621, 1984)) event.tagValue("title")?.takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (event.kind == 7) ReactionContent(event)
                else if (event.kind == 9802) HighlightContent(event, profiles, compact = true, onNavigate = onNavigate)
                else EventContent(event, profile, profiles, compact = true, onNavigate = onNavigate)
            }
            HorizontalDivider(color = Color(0xFF3D3D3D))
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(onClick = onAuthor).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile, event.pubkey, onAuthor, size = 22)
                    Spacer(Modifier.width(7.dp))
                    Text(profile?.name ?: Nip19.npubEncode(event.pubkey).let { it.take(10) + "…" }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(relativeTime(event.createdAt), color = muted, fontSize = 11.sp,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Search for this note") {
                        onNavigate("nostr:${Nip19.noteEncode(event.id)}")
                    }.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 8.dp))
                Row(Modifier.width(192.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(Icons.Outlined.ContentCopy, "Copy event ID", { clipboard.setText(AnnotatedString("nostr:${Nip19.noteEncode(event.id)}")) })
                ActionIcon(Icons.Outlined.PhoneAndroid, "Open in app", { openInNostrApp(context, event) })
                ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in browser", { openUrl(context, "https://njump.to/${Nip19.noteEncode(event.id)}") })
                ActionIcon(Icons.Outlined.MoreHoriz, "Event details and actions", onOpen)
                }
            }
        }
    }
}

@Composable
internal fun Avatar(profile: Profile?, pubkey: String, onClick: () -> Unit, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(blue.copy(alpha = 0.15f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text((profile?.name ?: pubkey).take(1).uppercase(), color = blue, fontWeight = FontWeight.Bold)
        profile?.picture?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun EventDetails(event: Nip01Event, profile: Profile?, profiles: Map<String, Profile>, onNavigate: (String) -> Unit, onAuthor: () -> Unit, raw: Boolean, initialScroll: Int, onScroll: (Int) -> Unit, onToggleRaw: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scrollState = remember(event.id) { ScrollState(initialScroll) }
    val saveScroll by rememberUpdatedState(onScroll)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value.takeUnless { scrollState.isScrollInProgress } }
            .filterNotNull().distinctUntilChanged().collectLatest { saveScroll(it) }
    }
    Column(modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 20.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile, event.pubkey, onAuthor); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile?.name ?: Nip19.npubEncode(event.pubkey).take(24) + "…", Modifier.clickable(onClick = onAuthor), fontWeight = FontWeight.Bold)
                Text("${kindLabel(event.kind)} · ${dateLabel(event.createdAt)}",
                    Modifier.clickable(role = Role.Button, onClickLabel = "Search for this note") {
                        onNavigate("nostr:${Nip19.noteEncode(event.id)}")
                    }.heightIn(min = 48.dp).wrapContentHeight(), color = muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!raw) ThreadContext(event, onNavigate)
        if (raw || event.kind !in setOf(30023, 1621, 1984)) event.tagValue("title")?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        CompositionLocalProvider(LocalArticleScroll provides scrollState) {
        SelectionContainer {
            when {
                raw -> Text(event.toJsonString(), fontFamily = FontFamily.Monospace)
                event.kind == 7 -> ReactionContent(event)
                event.kind == 9802 -> HighlightContent(event, profiles, compact = false, onNavigate = onNavigate)
                else -> EventContent(event, profile, profiles, compact = false, onNavigate = onNavigate)
            }
        }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ActionIcon(Icons.Outlined.PhoneAndroid, "Open in app", { openInNostrApp(context, event) })
            ActionIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Open in browser", { openUrl(context, "https://njump.to/${Nip19.noteEncode(event.id)}") })
            ActionIcon(Icons.Outlined.Share, "Share event", { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, eventUrl(event)) }, "Share event")) })
            ActionIcon(Icons.Outlined.ContentCopy, if (raw) "Copy event JSON" else "Copy event ID", { clipboard.setText(AnnotatedString(if (raw) event.toJsonString() else "nostr:${Nip19.noteEncode(event.id)}")) })
            ActionIcon(Icons.Outlined.DataObject, if (raw) "Show rendered event" else "Show raw event JSON", onToggleRaw, selected = raw)
        }
    }
}

@Composable
private fun RelayDialog(state: SearchState, model: SearchModel, onDismiss: () -> Unit) {
    val settings by model.appSettings.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(state.relays.joinToString("\n")) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Settings") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Rich link previews", Modifier.weight(1f))
                Switch(settings.richPreviews, model.appSettings::setRichPreviews)
            }
            Text("Previews contact linked websites.", style = MaterialTheme.typography.bodySmall, color = muted)
            if (state.pubkey != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(settings.sync, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = muted)
                    ActionIcon(Icons.Outlined.Refresh, "Sync app settings", model.appSettings::refresh)
                }
                Text("App settings sync publicly over Nostr. Search relays stay on this device.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            HorizontalDivider()
            Text("Search relays", style = MaterialTheme.typography.titleSmall)
            Text("Queries are sent to these relays. Choose relays that support Nostr text search (NIP-50).", color = muted)
            OutlinedTextField(text, { text = it; error = null }, Modifier.fillMaxWidth(), label = { Text("One wss:// URL per line") }, minLines = 5, isError = error != null, textStyle = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { text = defaultSearchRelays.joinToString("\n") }) { Text("Restore defaults") }
            if (state.statuses.isNotEmpty()) Text("LAST SEARCH", style = MaterialTheme.typography.labelSmall, color = muted)
            state.statuses.forEach { (url, status) -> Column { Text(url.removePrefix("wss://"), fontSize = 13.sp); Text(status, color = if (status == "Complete") blue else muted, fontSize = 12.sp) } }
            Text("Direct lookups also use Damus, nos.lol, and Primal. Public profile names and avatars are fetched from purplepag.es and Damus. Queries are visible to relays and image requests go to their hosts.", style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }, confirmButton = { TextButton(onClick = { error = model.setRelays(text); if (error == null) onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { Toast.makeText(context, "No app available to open this link.", Toast.LENGTH_SHORT).show() }
}
