package com.deckpuller.ui.pull

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard
import kotlinx.coroutines.launch

@Composable
fun PullRoute(
    onBack: () -> Unit,
    viewModel: PullViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var celebrationDismissed by remember { mutableStateOf(false) }
    // Re-arm the celebration whenever the deck drops back to incomplete, so re-completing
    // (e.g. after a reset or refresh) celebrates again instead of only once per screen.
    LaunchedEffect(state?.isComplete) {
        if (state?.isComplete == false) celebrationDismissed = false
    }

    state?.let { pull ->
        PullScreen(
            state = pull,
            isRefreshing = isRefreshing,
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
            onSearchChange = viewModel::onSearchChange,
            onFilterChange = viewModel::onFilterChange,
            onRefresh = viewModel::refresh,
            onReset = viewModel::reset,
            onBack = onBack,
            onCelebrationFinished = { celebrationDismissed = true },
            showCelebration = pull.isComplete && !celebrationDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullScreen(
    state: PullUiState,
    isRefreshing: Boolean,
    onIncrement: (DeckCard) -> Unit,
    onDecrement: (DeckCard) -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String?) -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onCelebrationFinished: () -> Unit,
    showCelebration: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var zoomedCard by remember { mutableStateOf<DeckCard?>(null) }

    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Opening search jumps focus to the field and raises the keyboard automatically.
    LaunchedEffect(searching) {
        if (searching) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val alphabetIndex = remember(state.cards) { buildAlphabetIndex(state.cards) }

    // Floating letter indicator: while scrubbing the rail it tracks the letter and
    // vertical position under the thumb; while the list flings it shows the first
    // visible card's initial, centered.
    var scrub by remember { mutableStateOf<RailScrub?>(null) }
    val scrollLetter by remember(state.cards) {
        derivedStateOf {
            state.cards.getOrNull(listState.firstVisibleItemIndex)
                ?.name?.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }
    }
    val bubbleLetter = scrub?.letter ?: scrollLetter
    val showLetterBubble = alphabetIndex.isNotEmpty() &&
        (scrub != null || listState.isScrollInProgress)
    val density = LocalDensity.current

    Scaffold(
        modifier = modifier,
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            if (!searching) {
                ActionsFab(
                    onFilter = { showFilterDialog = true },
                    onReset = { showResetDialog = true },
                    filterActive = state.activeFilter != null,
                )
            }
        },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back to decks")
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            placeholder = { Text("Search cards") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocus)
                                .semantics { contentDescription = "Search field" },
                        )
                    } else {
                        // Condensed: deck name with the pull count/percent tucked underneath.
                        val pct = if (state.total == 0) 0 else (state.pulled * 100 / state.total)
                        Column {
                            Text(
                                state.deckName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isRefreshing) "Refreshing…"
                                else "${state.pulled} / ${state.total} · $pct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // Slim progress bar pinned to the top, replacing the bulky header block.
                LinearProgressIndicator(
                    progress = { if (state.total == 0) 0f else state.pulled.toFloat() / state.total },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val areaHeightPx = constraints.maxHeight
                    val bubbleSizePx = with(density) { 64.dp.toPx() }
                    Row(modifier = Modifier.fillMaxSize()) {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                // Clear the speed-dial FAB so the last card stays tappable.
                                contentPadding = PaddingValues(bottom = 96.dp),
                            ) {
                                items(state.cards, key = { it.id }) { card ->
                                    CardRow(
                                        card = card,
                                        onIncrement = onIncrement,
                                        onDecrement = onDecrement,
                                        onImageClick = { zoomedCard = it },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                        if (alphabetIndex.isNotEmpty()) {
                            AlphabetRail(
                                enabled = alphabetIndex.keys,
                                onSelect = { letter ->
                                    alphabetIndex[letter]?.let { index ->
                                        scope.launch { listState.scrollToItem(index) }
                                    }
                                },
                                onScrubChange = { scrub = it },
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    LetterBubbleOverlay(
                        visible = showLetterBubble,
                        letter = bubbleLetter,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 48.dp)
                            .offset {
                                // Follow the thumb while scrubbing; otherwise sit centered.
                                val fraction = scrub?.fraction ?: 0.5f
                                val y = (fraction * areaHeightPx - bubbleSizePx / 2f)
                                    .toInt()
                                    .coerceIn(0, (areaHeightPx - bubbleSizePx).toInt().coerceAtLeast(0))
                                IntOffset(0, y)
                            },
                    )
                }
            }

            if (showCelebration) {
                CelebrationOverlay(onFinished = onCelebrationFinished)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset progress?") },
            text = { Text("This sets every card's pulled count back to zero for this deck.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onReset() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showFilterDialog) {
        FilterDialog(
            subtitles = state.subtitles,
            active = state.activeFilter,
            onSelect = { onFilterChange(it); showFilterDialog = false },
            onDismiss = { showFilterDialog = false },
        )
    }

    zoomedCard?.let { card ->
        CardImageDialog(card = card, onDismiss = { zoomedCard = null })
    }
}

/** Lets the user filter the list to a single subtitle (Archidekt category / type line). */
@Composable
private fun FilterDialog(
    subtitles: List<String>,
    active: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by category") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FilterOption(label = "All cards", selected = active == null) { onSelect(null) }
                subtitles.forEach { subtitle ->
                    FilterOption(label = subtitle, selected = active == subtitle) { onSelect(subtitle) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Expandable speed-dial FAB holding the deck actions (filter, reset). */
@Composable
private fun ActionsFab(
    onFilter: () -> Unit,
    onReset: () -> Unit,
    filterActive: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    // Left-anchored speed dial, so the FAB and its labels read outward from the left edge.
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            MiniAction(
                label = if (filterActive) "Filter (on)" else "Filter",
                icon = Icons.Filled.FilterList,
            ) { expanded = false; onFilter() }
            MiniAction("Reset progress", Icons.Filled.RestartAlt) { expanded = false; onReset() }
        }
        FloatingActionButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.MoreVert,
                contentDescription = if (expanded) "Close actions" else "Actions",
            )
        }
    }
}

@Composable
private fun MiniAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    // FAB-then-label so a left-anchored dial reads naturally rightward.
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Fades the floating letter bubble in/out. Standalone so the non-scoped AnimatedVisibility resolves. */
@Composable
private fun LetterBubbleOverlay(visible: Boolean, letter: Char, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LetterBubble(letter)
    }
}

/** Big circular letter that floats over the list while scrolling or scrubbing the rail. */
@Composable
private fun LetterBubble(letter: Char) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun CardImageDialog(card: DeckCard, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
