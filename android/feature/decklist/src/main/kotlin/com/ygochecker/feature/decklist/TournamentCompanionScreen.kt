package com.ygochecker.feature.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.StatusChip
import com.ygochecker.core.designsystem.StatusTone
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.TournamentRepository
import com.ygochecker.core.model.DeckNotes
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.MatchResult
import com.ygochecker.core.model.TournamentMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TournamentCompanionViewModel @Inject constructor(
    private val repo: TournamentRepository,
    listDecks: ListDecklists,
) : ViewModel() {
    private val deckId = MutableStateFlow<Long?>(null)
    val allDecks = listDecks.invoke().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notes = deckId.flatMapLatest { id -> id?.let(repo::observeNotes) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val matches = deckId.flatMapLatest { id -> id?.let(repo::observeMatches) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: Long) {
        deckId.value = id
    }

    fun saveNotes(strengths: String, weaknesses: String, strategy: String) = viewModelScope.launch {
        val id = deckId.value ?: return@launch
        repo.saveNotes(DeckNotes(id, strengths, weaknesses, strategy))
    }

    fun addMatch(
        roundLabel: String,
        opponent: String,
        opponentDeckId: Long?,
        result: MatchResult,
        games: List<MatchResult>,
        sideNotes: String,
        notes: String,
    ) = viewModelScope.launch {
        val id = deckId.value ?: return@launch
        repo.addMatch(
            TournamentMatch(
                deckId = id,
                roundLabel = roundLabel,
                opponent = opponent,
                opponentDeckId = opponentDeckId,
                result = result,
                games = games,
                sideNotes = sideNotes,
                notes = notes,
            ),
        )
    }

    fun deleteMatch(id: Long) = viewModelScope.launch { repo.deleteMatch(id) }
}

@Composable
fun TournamentCompanionDialog(deck: Decklist, onDismiss: () -> Unit) {
    val vm: TournamentCompanionViewModel = hiltViewModel()
    val allDecks by vm.allDecks.collectAsStateWithLifecycle()
    var currentDeckId by remember(deck.id) { mutableStateOf(deck.id) }
    val currentDeck = allDecks.firstOrNull { it.id == currentDeckId } ?: deck
    LaunchedEffect(currentDeckId) { vm.select(currentDeckId) }
    val notes by vm.notes.collectAsStateWithLifecycle()
    val matches by vm.matches.collectAsStateWithLifecycle()
    var strengths by remember(notes) { mutableStateOf(notes?.strengths.orEmpty()) }
    var weaknesses by remember(notes) { mutableStateOf(notes?.weaknesses.orEmpty()) }
    var strategy by remember(notes) { mutableStateOf(notes?.strategy.orEmpty()) }
    var addMatchOpen by remember { mutableStateOf(false) }
    var duelHelperOpen by remember { mutableStateOf(false) }
    var deckPickerOpen by remember { mutableStateOf(false) }
    var prefilledResult by remember { mutableStateOf<MatchResult?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(DuelSpacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                    Text(
                        stringResource(DesignR.string.tournament_title, currentDeck.name),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { deckPickerOpen = true },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { deckPickerOpen = true }) {
                        Icon(Icons.Default.SwapHoriz, stringResource(DesignR.string.tournament_switch_deck))
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DuelSpacing.space4)
                        .padding(bottom = DuelSpacing.space4),
                    verticalArrangement = Arrangement.spacedBy(DuelSpacing.space4),
                ) {
                    FilledTonalButton(onClick = { duelHelperOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Bolt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(DesignR.string.tournament_open_duel_helper))
                    }
                    Text(stringResource(DesignR.string.tournament_notes_title), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = strengths,
                        onValueChange = { strengths = it },
                        label = { Text(stringResource(DesignR.string.tournament_strengths)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = weaknesses,
                        onValueChange = { weaknesses = it },
                        label = { Text(stringResource(DesignR.string.tournament_weaknesses)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = strategy,
                        onValueChange = { strategy = it },
                        label = { Text(stringResource(DesignR.string.tournament_strategy)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Button(
                        onClick = { vm.saveNotes(strengths, weaknesses, strategy) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(DesignR.string.tournament_save_notes))
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(DesignR.string.tournament_matches_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = { addMatchOpen = true }) {
                            Icon(Icons.Default.Add, stringResource(DesignR.string.tournament_add_match))
                        }
                    }
                    if (matches.isEmpty()) {
                        Text(
                            stringResource(DesignR.string.tournament_matches_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        matches.forEach { match ->
                            MatchRow(match, onDelete = { vm.deleteMatch(match.id) })
                        }
                    }
                }
            }
        }
    }

    if (addMatchOpen) {
        AddMatchSheet(
            deck = currentDeck,
            allDecks = allDecks,
            initialResult = prefilledResult,
            onDismiss = { addMatchOpen = false; prefilledResult = null },
            onSave = { round, opponent, opponentDeckId, result, games, sideNotes, freeNotes ->
                vm.addMatch(round, opponent, opponentDeckId, result, games, sideNotes, freeNotes)
                addMatchOpen = false
                prefilledResult = null
            },
        )
    }
    if (duelHelperOpen) {
        DuelHelperDialog(
            onDismiss = { duelHelperOpen = false },
            onRegisterResult = { result ->
                duelHelperOpen = false
                prefilledResult = result
                addMatchOpen = true
            },
        )
    }
    if (deckPickerOpen) {
        DeckPickerSheet(
            title = stringResource(DesignR.string.tournament_switch_deck),
            decks = allDecks.filterNot(Decklist::isExternal),
            onPick = { currentDeckId = it.id; deckPickerOpen = false },
            onDismiss = { deckPickerOpen = false },
        )
    }
}

@Composable
private fun MatchRow(match: TournamentMatch, onDelete: () -> Unit) {
    val tone = when (match.result) {
        MatchResult.WIN -> StatusTone.Success
        MatchResult.LOSS -> StatusTone.Error
        MatchResult.DRAW -> StatusTone.Neutral
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(DuelSpacing.space3), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${match.roundLabel} · ${match.opponent}".trim(' ', '·'),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (match.games.isNotEmpty()) {
                        Text(
                            match.games.joinToString(" ") { gameShortLabel(it) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusChip(matchResultLabel(match.result), tone)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(DesignR.string.decks_delete))
                }
            }
            if (match.sideNotes.isNotBlank()) {
                Text(match.sideNotes, style = MaterialTheme.typography.bodySmall)
            }
            if (match.notes.isNotBlank()) {
                Text(match.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun matchResultLabel(result: MatchResult): String = stringResource(
    when (result) {
        MatchResult.WIN -> DesignR.string.tournament_result_win
        MatchResult.LOSS -> DesignR.string.tournament_result_loss
        MatchResult.DRAW -> DesignR.string.tournament_result_draw
    },
)

private fun gameShortLabel(result: MatchResult) = when (result) {
    MatchResult.WIN -> "W"
    MatchResult.LOSS -> "L"
    MatchResult.DRAW -> "D"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddMatchSheet(
    deck: Decklist,
    allDecks: List<Decklist>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, MatchResult, List<MatchResult>, String, String) -> Unit,
    initialResult: MatchResult? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var roundLabel by remember { mutableStateOf("") }
    var opponent by remember { mutableStateOf("") }
    var opponentDeckId by remember { mutableStateOf<Long?>(null) }
    var opponentDeckPickerOpen by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(initialResult ?: MatchResult.WIN) }
    var games by remember { mutableStateOf(listOf<MatchResult?>(null, null, null)) }
    var freeNotes by remember { mutableStateOf("") }
    var swapOut by remember { mutableStateOf(setOf<Int>()) }
    var swapIn by remember { mutableStateOf(setOf<Int>()) }
    val mainCards = remember(deck) { deck.cards.filter { it.section == DeckSection.MAIN } }
    val sideCards = remember(deck) { deck.cards.filter { it.section == DeckSection.SIDE } }
    val opponentDeck = allDecks.firstOrNull { it.id == opponentDeckId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = DuelSpacing.space4)
                .padding(bottom = DuelSpacing.space4)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            Text(stringResource(DesignR.string.tournament_add_match), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = roundLabel,
                onValueChange = { roundLabel = it },
                label = { Text(stringResource(DesignR.string.tournament_round)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = opponent,
                onValueChange = { opponent = it },
                label = { Text(stringResource(DesignR.string.tournament_opponent)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(onClick = { opponentDeckPickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Style, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    opponentDeck?.name
                        ?: stringResource(DesignR.string.tournament_opponent_deck_pick),
                )
            }
            Text(stringResource(DesignR.string.tournament_result), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatchResult.entries.forEach { r ->
                    FilterChip(selected = result == r, onClick = { result = r }, label = { Text(matchResultLabel(r)) })
                }
            }
            Text(stringResource(DesignR.string.tournament_games), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                games.forEachIndexed { index, g ->
                    FilterChip(
                        selected = g != null,
                        onClick = {
                            games = games.toMutableList().also {
                                it[index] = when (g) {
                                    null -> MatchResult.WIN
                                    MatchResult.WIN -> MatchResult.LOSS
                                    MatchResult.LOSS -> MatchResult.DRAW
                                    MatchResult.DRAW -> null
                                }
                            }
                        },
                        label = { Text("G${index + 1}${g?.let { " · " + gameShortLabel(it) }.orEmpty()}") },
                    )
                }
            }
            if (mainCards.isNotEmpty() || sideCards.isNotEmpty()) {
                Text(stringResource(DesignR.string.tournament_side_out), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    mainCards.forEach { dc ->
                        FilterChip(
                            selected = dc.card.id in swapOut,
                            onClick = { swapOut = swapOut.toggle(dc.card.id) },
                            label = { Text(dc.card.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                Text(stringResource(DesignR.string.tournament_side_in), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sideCards.forEach { dc ->
                        FilterChip(
                            selected = dc.card.id in swapIn,
                            onClick = { swapIn = swapIn.toggle(dc.card.id) },
                            label = { Text(dc.card.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = freeNotes,
                onValueChange = { freeNotes = it },
                label = { Text(stringResource(DesignR.string.tournament_match_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Button(
                onClick = {
                    val outNames = mainCards.filter { it.card.id in swapOut }.map { it.card.name }
                    val inNames = sideCards.filter { it.card.id in swapIn }.map { it.card.name }
                    val sideNotes = buildList {
                        outNames.forEach { add("-$it") }
                        inNames.forEach { add("+$it") }
                    }.joinToString(", ")
                    onSave(roundLabel, opponent, opponentDeckId, result, games.filterNotNull(), sideNotes, freeNotes)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = opponent.isNotBlank(),
            ) {
                Text(stringResource(DesignR.string.tournament_save_match))
            }
        }
    }
    if (opponentDeckPickerOpen) {
        DeckPickerSheet(
            title = stringResource(DesignR.string.tournament_opponent_deck_pick),
            decks = allDecks,
            onPick = {
                opponentDeckId = it.id
                if (opponent.isBlank()) opponent = it.name
                opponentDeckPickerOpen = false
            },
            onDismiss = { opponentDeckPickerOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckPickerSheet(title: String, decks: List<Decklist>, onPick: (Decklist) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = DuelSpacing.space4).padding(bottom = DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (decks.isEmpty()) {
                Text(
                    stringResource(DesignR.string.tournament_no_decks_to_pick),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    decks.forEach { d ->
                        Surface(
                            onClick = { onPick(d) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(DuelSpacing.space3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.isExternal) {
                                    StatusChip(d.groupName ?: stringResource(DesignR.string.decks_external_section), StatusTone.Neutral)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Set<Int>.toggle(id: Int): Set<Int> = if (id in this) this - id else this + id

@Composable
private fun DuelHelperDialog(onDismiss: () -> Unit, onRegisterResult: (MatchResult) -> Unit) {
    var lp1 by remember { mutableStateOf(8000) }
    var lp2 by remember { mutableStateOf(8000) }
    var activePlayer by remember { mutableStateOf(1) }
    var running by remember { mutableStateOf(false) }
    var elapsed1 by remember { mutableLongStateOf(0L) }
    var elapsed2 by remember { mutableLongStateOf(0L) }
    val defeated = when {
        lp1 <= 0 -> 1
        lp2 <= 0 -> 2
        else -> null
    }

    LaunchedEffect(running, activePlayer, defeated) {
        if (!running || defeated != null) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (isActive) {
            delay(200)
            val now = System.currentTimeMillis()
            val dt = now - last
            last = now
            if (activePlayer == 1) elapsed1 += dt else elapsed2 += dt
        }
    }
    LaunchedEffect(defeated) { if (defeated != null) running = false }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(DuelSpacing.space4), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(DesignR.string.tournament_duel_helper),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                if (defeated != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (defeated == 1) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DuelSpacing.space4),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(DuelSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                        ) {
                            Text(
                                stringResource(
                                    if (defeated == 1) DesignR.string.tournament_you_lost else DesignR.string.tournament_you_won,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = { onRegisterResult(if (defeated == 1) MatchResult.LOSS else MatchResult.WIN) }) {
                                Text(stringResource(DesignR.string.tournament_register_result))
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = DuelSpacing.space4),
                    verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                ) {
                    PlayerClockPanel(
                        label = stringResource(DesignR.string.tournament_you),
                        lp = lp1,
                        onLpChange = { lp1 = (lp1 + it).coerceAtLeast(0) },
                        elapsedMs = elapsed1,
                        active = activePlayer == 1 && running,
                        modifier = Modifier.weight(1f),
                        onTapClock = {
                            if (!running) running = true
                            activePlayer = 2
                        },
                    )
                    PlayerClockPanel(
                        label = stringResource(DesignR.string.tournament_opponent),
                        lp = lp2,
                        onLpChange = { lp2 = (lp2 + it).coerceAtLeast(0) },
                        elapsedMs = elapsed2,
                        active = activePlayer == 2 && running,
                        modifier = Modifier.weight(1f),
                        onTapClock = {
                            if (!running) running = true
                            activePlayer = 1
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(DuelSpacing.space4),
                    horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                ) {
                    FilledTonalButton(onClick = { running = !running }, enabled = defeated == null, modifier = Modifier.weight(1f)) {
                        Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (running) DesignR.string.tournament_pause else DesignR.string.tournament_resume))
                    }
                    FilledTonalButton(
                        onClick = {
                            lp1 = 8000; lp2 = 8000; elapsed1 = 0; elapsed2 = 0; running = false; activePlayer = 1
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Restore, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(DesignR.string.tournament_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerClockPanel(
    label: String,
    lp: Int,
    onLpChange: (Int) -> Unit,
    elapsedMs: Long,
    active: Boolean,
    onTapClock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(DuelSpacing.space3),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(lp.toString(), style = MaterialTheme.typography.displayMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-1000, -500, -100).forEach { delta ->
                    FilledTonalButton(onClick = { onLpChange(delta) }) { Text(delta.toString()) }
                }
                listOf(100, 500, 1000).forEach { delta ->
                    FilledTonalButton(onClick = { onLpChange(delta) }) { Text("+$delta") }
                }
            }
            Text(formatClock(elapsedMs), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onTapClock) { Text(stringResource(DesignR.string.tournament_pass_turn)) }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
