package com.deckpuller.ui.pull

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.deckpuller.domain.model.DeckCard
import com.deckpuller.ui.common.scryfallBackFaceUrl
import com.deckpuller.ui.common.scryfallGenericCardBackUrl
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PullRoute(
    onBack: () -> Unit,
    onShoppingList: () -> Unit,
    viewModel: PullViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val commanderColors by viewModel.commanderColors.collectAsStateWithLifecycle()
    var celebrationDismissed by remember { mutableStateOf(false) }
    // Re-arm the celebration whenever the deck drops back to incomplete, so re-completing
    // (e.g. after a reset or refresh) celebrates again instead of only once per screen.
    LaunchedEffect(state?.isComplete) {
        if (state?.isComplete == false) celebrationDismissed = false
    }

    state?.let { pull ->
        // Recolour the whole screen from the commander's colour identity.
        CommanderColorTheme(colors = commanderColors) {
            PullScreen(
                state = pull,
                isRefreshing = isRefreshing,
                onIncrement = viewModel::increment,
                onDecrement = viewModel::decrement,
                onSearchChange = viewModel::onSearchChange,
                onFilterToggle = viewModel::onFilterToggle,
                onClearFilters = viewModel::onClearFilters,
                onRefresh = viewModel::refresh,
                onReset = viewModel::reset,
                onBack = onBack,
                onShoppingList = onShoppingList,
                onCelebrationFinished = { celebrationDismissed = true },
                showCelebration = pull.isComplete && !celebrationDismissed,
                celebrationColors = manaColors(commanderColors),
            )
        }
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
    onFilterToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onShoppingList: () -> Unit,
    onCelebrationFinished: () -> Unit,
    showCelebration: Boolean = false,
    celebrationColors: List<Color> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var zoomedCard by remember { mutableStateOf<DeckCard?>(null) }
    // The just-completed card, exploding into pieces where it sat in the list.
    var shatteringCard by remember { mutableStateOf<ShatteringCard?>(null) }
    // Commander art's root-space bounds — the spawn point for the deck-complete cascade.
    var commanderBounds by remember { mutableStateOf(Rect.Zero) }

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
    // vertical position under the thumb; while the list flings on its own it shows the
    // first visible card's initial, hovering just above centre.
    var scrub by remember { mutableStateOf<RailScrub?>(null) }
    val scrollLetter by remember(state.cards) {
        derivedStateOf {
            state.cards.getOrNull(listState.firstVisibleItemIndex)
                ?.name?.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }
    }
    // After a scrub ends the list keeps settling (a programmatic scroll), which would
    // otherwise flash the centred scroll-bubble — making it "pop up in the middle" of
    // the rail. Suppress the scroll-driven bubble from the moment a scrub begins until
    // the list comes fully to rest.
    var suppressScrollBubble by remember { mutableStateOf(false) }
    LaunchedEffect(scrub != null) {
        if (scrub != null) {
            suppressScrollBubble = true
        } else {
            // The rail's jump is launched asynchronously, so the programmatic scroll can
            // begin a frame or two AFTER the finger lifts. Hold the suppression past that
            // window, then wait for the list to come fully to rest — otherwise the bubble
            // flashes in the middle when scrubbing to the very top or bottom.
            delay(300)
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            suppressScrollBubble = false
        }
    }
    val bubbleLetter = scrub?.letter ?: scrollLetter
    val showLetterBubble = alphabetIndex.isNotEmpty() && (
        scrub != null ||
            // A pull-to-refresh also scrolls the list, but shouldn't summon the bubble.
            (listState.isScrollInProgress && !suppressScrollBubble && !isRefreshing)
        )
    // The bubble's letter and position are frozen while it's hidden, so the fade-out
    // after lifting off the rail doesn't visibly jump to the scroll position/letter —
    // it just fades where it sat. They only refresh while the bubble is actually shown.
    var bubbleScrubbing by remember { mutableStateOf(false) }
    var bubbleFraction by remember { mutableStateOf(0.28f) }
    var renderedLetter by remember { mutableStateOf(bubbleLetter) }
    LaunchedEffect(showLetterBubble, scrub, bubbleLetter) {
        if (showLetterBubble) {
            bubbleScrubbing = scrub != null
            bubbleFraction = scrub?.fraction ?: 0.28f
            renderedLetter = bubbleLetter
        }
    }
    val density = LocalDensity.current

    // The whole screen lives in this box so the deck-complete celebration can be layered
    // above the Scaffold (and its top bar) and darken everything.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier,
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (!searching) {
                ActionsFab(
                    onFilter = { showFilterDialog = true },
                    onReset = { showResetDialog = true },
                    activeFilterCount = state.activeFilters.size,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        CompactSearchField(
                            query = state.searchQuery,
                            onSearchChange = onSearchChange,
                            focusRequester = searchFocus,
                        )
                    } else {
                        // Condensed: commander art + deck name with the pull count/percent
                        // tucked underneath. (The refresh spinner lives in the actions row.)
                        val pct = if (state.total == 0) 0 else (state.pulled * 100 / state.total)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.commander?.let { commander ->
                                AsyncImage(
                                    model = commander.imageUrl,
                                    contentDescription = "Commander: ${commander.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 30.dp, height = 42.dp)
                                        .onGloballyPositioned { commanderBounds = it.boundsInRoot() }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { zoomedCard = commander },
                                )
                            }
                            Column(modifier = Modifier.weight(1f, fill = false)) {
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
                                if (state.collectionPresent) {
                                    Text(
                                        text = "You own ${state.ownedCards}/${state.ownedTotalCards} cards",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!searching) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
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
                        // Refresh spinner sits just left of the search button.
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(18.dp),
                            )
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onShoppingList) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Buy missing cards")
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Outer box is NOT padded, so it shares the root coordinate space the
        // fly-into-deck animation measures against; the content box keeps the inset.
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val areaHeightPx = constraints.maxHeight
                    val areaWidthPx = constraints.maxWidth
                    val bubbleSizePx = with(density) { 64.dp.toPx() }
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Rail on the LEFT edge; the list fills the rest to its right.
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
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = onRefresh,
                            // The refresh feedback lives by the title now, so hide the
                            // default drop-down indicator.
                            indicator = {},
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
                                        collectionPresent = state.collectionPresent,
                                        onCardCompleted = { c, bounds ->
                                            shatteringCard = ShatteringCard(c.imageUrl, bounds)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    LetterBubbleOverlay(
                        visible = showLetterBubble,
                        letter = renderedLetter,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                // While scrubbing, sit just inside the left rail and track
                                // the thumb; while flinging, centre over the list and hover
                                // just above the middle of the screen. Uses the frozen
                                // values so the exit fade doesn't jump.
                                val xPx = if (bubbleScrubbing) {
                                    with(density) { 48.dp.toPx() }
                                } else {
                                    (areaWidthPx - bubbleSizePx) / 2f
                                }
                                val yPx = (bubbleFraction * areaHeightPx - bubbleSizePx / 2f)
                                    .toInt()
                                    .coerceIn(0, (areaHeightPx - bubbleSizePx).toInt().coerceAtLeast(0))
                                IntOffset(xPx.toInt().coerceAtLeast(0), yPx)
                            },
                    )
                }
            }
          }

          shatteringCard?.let { shard ->
              CardShatterOverlay(shard = shard) { shatteringCard = null }
          }
        }
    }

    if (showCelebration) {
        // Layered above the Scaffold so the scrim darkens the entire screen, top bar
        // included. Banner + confetti first, then the Solitaire cascade on top. It stays
        // until the user taps anywhere to dismiss.
        CelebrationOverlay(
            onFinished = onCelebrationFinished,
            colors = celebrationColors,
        )
        DeckCompletionCascade(
            imageUrls = state.cards.mapNotNull { it.imageUrl },
            source = commanderBounds,
        )
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
            active = state.activeFilters,
            onToggle = onFilterToggle,
            onClear = onClearFilters,
            onDismiss = { showFilterDialog = false },
        )
    }

    zoomedCard?.let { card ->
        CardImageDialog(card = card, onDismiss = { zoomedCard = null })
    }
}

/**
 * Multi-select category filter shown as wrap-around chips. A card is kept if it matches
 * ANY selected category; with nothing selected, every card shows. Tapping a chip toggles
 * it, "Clear all" resets, and the header keeps a running tally so the state is obvious.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(
    subtitles: List<String>,
    active: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by category") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (active.isEmpty()) "Showing all cards"
                    else "Showing ${active.size} of ${subtitles.size} categories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    subtitles.forEach { subtitle ->
                        val selected = subtitle in active
                        FilterChip(
                            selected = selected,
                            onClick = { onToggle(subtitle) },
                            label = { Text(subtitle) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (active.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear all") }
            }
        },
    )
}

/** Expandable speed-dial FAB holding the deck actions (filter, reset). */
@Composable
private fun ActionsFab(
    onFilter: () -> Unit,
    onReset: () -> Unit,
    activeFilterCount: Int,
) {
    var expanded by remember { mutableStateOf(false) }

    // Right-anchored speed dial, so the FAB and its labels read inward from the right edge.
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            MiniAction(
                label = if (activeFilterCount > 0) "Filter ($activeFilterCount)" else "Filter",
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
    // Label-then-FAB so a right-anchored dial reads naturally inward (leftward).
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }
}

/**
 * A shorter, pill-shaped single-line search field for the top bar. Built on
 * BasicTextField + DecorationBox so its height can be trimmed below the default
 * 56dp Material text-field height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactSearchField(
    query: String,
    onSearchChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = query,
        onValueChange = onSearchChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "Search field" },
    ) { inner ->
        TextFieldDefaults.DecorationBox(
            value = query,
            innerTextField = inner,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interaction,
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
            // Trim the vertical padding so the field is noticeably shorter than the
            // default Material text field.
            contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                top = 4.dp,
                bottom = 4.dp,
            ),
        )
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

/** Standard Magic card aspect ratio (63mm × 88mm). */
private const val CARD_RATIO = 0.716f

/** Max pitch (degrees) the card tilts when dragged vertically; it springs back to flat. */
private const val MAX_PITCH_DEGREES = 24f

/** Degrees of yaw per full-width horizontal drag — enough to comfortably flip past 180°. */
private const val YAW_PER_WIDTH = 220f

/**
 * Full-screen card viewer. The card is a real 3D object: drag horizontally to spin it
 * around and see its back (it snaps to whichever face you let go nearest), drag vertically
 * to tilt it (that springs back to flat). The front of a foil catches a holographic sheen
 * that slides with the motion; the back never does. The back is the card's true reverse
 * for double-faced cards, otherwise the standard Magic card back. Tapping outside dismisses.
 */
@Composable
private fun CardImageDialog(card: DeckCard, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scope = rememberCoroutineScope()
        // Drag writes these directly (synchronous and lossless — the canonical Compose drag
        // pattern); release animates them home. Driving an Animatable via a launched snapTo
        // per drag delta drops deltas under rapid input, so yaw never accumulates enough to
        // flip past 90°.
        var yaw by remember { mutableFloatStateOf(0f) }
        var pitch by remember { mutableFloatStateOf(0f) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val springBack = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
        val snapFace = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

        // Which face points at us: the back is showing while yaw sits in the rear half-turn.
        val norm = ((yaw % 360f) + 360f) % 360f
        val showBack = norm > 90f && norm < 270f

        val idleTransition = rememberInfiniteTransition(label = "foil-idle")
        val idle by idleTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "foil-idle-sweep",
        )
        // Idle drift plus a slide that tracks how far the card is turned/tilted.
        val sweep = idle + sin(yaw * PI.toFloat() / 180f) * 0.6f -
            (pitch / MAX_PITCH_DEGREES) * 0.4f

        // Back face: the real reverse of a double-faced card, falling back to the standard
        // Magic back when that 404s (single-faced cards have no real back).
        val genericBack = remember { scryfallGenericCardBackUrl("normal") }
        var backUrl by remember(card.scryfallId) {
            mutableStateOf(scryfallBackFaceUrl(card.scryfallId, "normal") ?: genericBack)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(CARD_RATIO)
                    .graphicsLayer {
                        rotationY = yaw
                        rotationX = pitch
                        cameraDistance = 16f * density
                    }
                    .pointerInput(Unit) {
                        // On release, spring the tilt flat and snap yaw to the nearest face.
                        val settle: () -> Unit = {
                            settleJob = scope.launch {
                                launch { animate(pitch, 0f, animationSpec = springBack) { v, _ -> pitch = v } }
                                val target = (yaw / 180f).roundToInt() * 180f
                                animate(yaw, target, animationSpec = snapFace) { v, _ -> yaw = v }
                            }
                        }
                        detectDragGestures(
                            onDragStart = { settleJob?.cancel() },
                            onDragEnd = settle,
                            onDragCancel = settle,
                        ) { change, drag ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            yaw += drag.x / w * YAW_PER_WIDTH
                            pitch = (pitch - drag.y / h * 60f).coerceIn(-MAX_PITCH_DEGREES, MAX_PITCH_DEGREES)
                        }
                    },
            ) {
                if (showBack) {
                    AsyncImage(
                        model = backUrl,
                        contentDescription = "${card.name} (back)",
                        contentScale = ContentScale.Crop,
                        // Counter-rotate so the back reads upright (not mirrored) at 180°.
                        onError = { if (backUrl != genericBack) backUrl = genericBack },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                            .clip(RoundedCornerShape(14.dp)),
                    )
                } else {
                    val foil = if (card.isFoil) {
                        Modifier.foilSheen(sweep = sweep, shape = RoundedCornerShape(14.dp), intensity = 0.9f)
                    } else {
                        Modifier
                    }
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .then(foil),
                    )
                }
            }
        }
    }
}
