package com.ygochecker.feature.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.EmptyState
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.SettingsGroup
import com.ygochecker.core.designsystem.StatusChip
import com.ygochecker.core.designsystem.StatusTone
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.domain.DeckFlowLinkRepository
import com.ygochecker.core.domain.FindSimultaneousTriggers
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.GetFlow
import com.ygochecker.core.domain.GetSegocProfiles
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.ListFlows
import com.ygochecker.core.model.FlowEdge
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowKind
import com.ygochecker.core.model.FlowRehearsalEngine
import com.ygochecker.core.model.FlowSummary
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.SegocProfileSummary
import com.ygochecker.core.model.SimultaneousTriggerPair
import com.ygochecker.core.model.TimingAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FlowViewModel @Inject constructor(
    formatPreference: FormatPreference,
    listDecks: ListDecklists,
    private val listFlows: ListFlows,
    private val getFlow: GetFlow,
    private val flowLinks: DeckFlowLinkRepository,
    private val getSegocProfiles: GetSegocProfiles,
    private val findSimultaneousTriggers: FindSimultaneousTriggers,
) : ViewModel() {
    val format = formatPreference.values.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GameFormat.HAT,
    )
    val decks = listDecks.invoke().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    var summaries by mutableStateOf<List<FlowSummary>>(emptyList())
        private set
    var linkedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var focusDeckId by mutableStateOf<Long?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var activeGraph by mutableStateOf<FlowGraph?>(null)
        private set
    var engine by mutableStateOf<FlowRehearsalEngine?>(null)
        private set
    var timingError by mutableStateOf(false)
        private set
    var kindFilter by mutableStateOf<FlowKind?>(null)
        private set
    var activeDeckId by mutableStateOf<Long?>(null)
        private set
    var segocByCardId by mutableStateOf<Map<Int, SegocProfileSummary>>(emptyMap())
        private set
    var simultaneousPairs by mutableStateOf<List<SimultaneousTriggerPair>>(emptyList())
        private set
    var showCatalog by mutableStateOf(false)
        private set
    var coachLoading by mutableStateOf(false)
        private set

    fun toggleCatalog() {
        showCatalog = !showCatalog
    }

    fun loadCoachForActiveDeck() {
        val deckId = decks.value.maxByOrNull { it.updatedAt }?.id
        activeDeckId = deckId
        if (deckId == null) {
            segocByCardId = emptyMap()
            simultaneousPairs = emptyList()
            return
        }
        viewModelScope.launch {
            coachLoading = true
            val deck = decks.value.first { it.id == deckId }
            val cardIds = deck.cards.map { it.card.id }.distinct()
            segocByCardId = getSegocProfiles.invoke(cardIds).associateBy { it.cardId }
            simultaneousPairs = findSimultaneousTriggers.invoke(deckId)
            coachLoading = false
        }
    }

    fun refresh(format: GameFormat) {
        viewModelScope.launch {
            loading = true
            summaries = listFlows.invoke(format)
            val deckId = focusDeckId ?: decks.value.firstOrNull()?.id
            linkedIds = if (deckId != null) {
                flowLinks.flowIdsForDeck(deckId).toSet()
            } else {
                emptySet()
            }
            loading = false
        }
    }

    fun focusDeck(id: Long?) {
        focusDeckId = id
        viewModelScope.launch {
            linkedIds = if (id != null) flowLinks.flowIdsForDeck(id).toSet() else emptySet()
        }
    }

    fun toggleKindFilter(kind: FlowKind?) {
        kindFilter = if (kindFilter == kind) null else kind
    }

    fun openFlow(id: String) {
        viewModelScope.launch {
            val graph = getFlow.invoke(id) ?: return@launch
            activeGraph = graph
            engine = FlowRehearsalEngine(graph)
            timingError = false
        }
    }

    fun closeFlow() {
        activeGraph = null
        engine = null
        timingError = false
    }

    fun restart() {
        val g = activeGraph ?: return
        engine = FlowRehearsalEngine(g)
        timingError = false
    }

    fun advanceDefault(chainLink: Int = 1) {
        val eng = engine ?: return
        if (!eng.mustPassTimingGate(TimingAnswer(chainLink))) {
            timingError = true
            return
        }
        timingError = false
        eng.advanceDefault(TimingAnswer(chainLink))
        engine = FlowRehearsalEngine(eng.graph, eng.currentId)
    }

    fun choose(edge: FlowEdge, chainLink: Int = 1) {
        val eng = engine ?: return
        if (!eng.mustPassTimingGate(TimingAnswer(chainLink))) {
            timingError = true
            return
        }
        timingError = false
        eng.choose(edge, TimingAnswer(chainLink))
        engine = FlowRehearsalEngine(eng.graph, eng.currentId)
    }

    fun filteredSummaries(): List<FlowSummary> {
        var list = summaries
        if (linkedIds.isNotEmpty()) {
            list = list.sortedByDescending { it.id in linkedIds }
        }
        val f = kindFilter ?: return list
        return list.filter { it.kind == f }
    }
}

@Composable
fun FlowRoute(vm: FlowViewModel = hiltViewModel()) {
    val format by vm.format.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    LaunchedEffect(format, decks) { vm.refresh(format) }
    LaunchedEffect(decks) { vm.loadCoachForActiveDeck() }

    val graph = vm.activeGraph
    val eng = vm.engine
    when {
        graph != null && eng != null -> FlowRehearsalScreen(
            graph = graph,
            engine = eng,
            timingError = vm.timingError,
            onBack = vm::closeFlow,
            onRestart = vm::restart,
            onAdvanceDefault = { vm.advanceDefault(1) },
            onAdvanceFailTiming = { vm.advanceDefault(2) },
            onChoose = { edge -> vm.choose(edge, 1) },
        )
        vm.showCatalog -> FlowCatalogScreen(
            format = format,
            loading = vm.loading,
            summaries = vm.filteredSummaries(),
            linkedIds = vm.linkedIds,
            decks = decks,
            focusDeckId = vm.focusDeckId ?: decks.firstOrNull()?.id,
            kindFilter = vm.kindFilter,
            onKindFilter = vm::toggleKindFilter,
            onFocusDeck = vm::focusDeck,
            onOpen = vm::openFlow,
            onBackToCoach = vm::toggleCatalog,
        )
        else -> {
            val activeDeck = decks.firstOrNull { it.id == vm.activeDeckId }
            FlowCoachScreen(
                deck = activeDeck,
                loading = vm.coachLoading,
                segocByCardId = vm.segocByCardId,
                simultaneousPairs = vm.simultaneousPairs,
                onOpenCatalog = vm::toggleCatalog,
            )
        }
    }
}

@Composable
private fun FlowCoachScreen(
    deck: com.ygochecker.core.model.Decklist?,
    loading: Boolean,
    segocByCardId: Map<Int, SegocProfileSummary>,
    simultaneousPairs: List<SimultaneousTriggerPair>,
    onOpenCatalog: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ThemedScreenHeader(
            title = stringResource(DesignR.string.flow_coach_title),
            subtitle = stringResource(DesignR.string.flow_coach_subtitle),
        )
        when {
            deck == null -> EmptyState(
                icon = Icons.Default.AccountTree,
                title = stringResource(DesignR.string.flow_coach_empty_title),
                body = stringResource(DesignR.string.flow_coach_empty_body),
                modifier = Modifier.weight(1f).padding(DuelSpacing.space4),
            )
            loading -> Text(
                text = stringResource(DesignR.string.flow_loading),
                modifier = Modifier.padding(DuelSpacing.space4),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(DuelSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                modifier = Modifier.weight(1f),
            ) {
                if (simultaneousPairs.isNotEmpty()) {
                    item(key = "segoc-header") {
                        Text(
                            text = stringResource(DesignR.string.flow_segoc_section_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(simultaneousPairs, key = { "${it.cardAId}-${it.cardBId}-${it.sharedEvent}" }) { pair ->
                        SegocWarningCard(pair, deck)
                    }
                }
                item(key = "cards-header") {
                    Text(
                        text = stringResource(DesignR.string.flow_coach_cards_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(deck.cards, key = { it.card.id }) { deckCard ->
                    FlowCoachCardRow(deckCard.card, segocByCardId[deckCard.card.id])
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(DuelSpacing.space4)) {
            OutlinedButton(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(DesignR.string.flow_open_catalog))
            }
        }
    }
}

@Composable
private fun SegocWarningCard(pair: SimultaneousTriggerPair, deck: com.ygochecker.core.model.Decklist) {
    val nameA = deck.cards.firstOrNull { it.card.id == pair.cardAId }?.card?.name ?: pair.cardAId.toString()
    val nameB = deck.cards.firstOrNull { it.card.id == pair.cardBId }?.card?.name ?: pair.cardBId.toString()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(DuelSpacing.space4), verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2)) {
            Text("$nameA + $nameB", style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(DesignR.string.flow_segoc_rule_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = segocTacticalNote(pair.sharedEvent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun segocTacticalNote(event: com.ygochecker.core.model.TriggerEvent): String =
    stringResource(DesignR.string.flow_segoc_generic_note)

@Composable
private fun FlowCoachCardRow(
    card: com.ygochecker.core.model.Card,
    segoc: SegocProfileSummary?,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(DuelSpacing.space3),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            Text(card.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (segoc != null) {
                val speedLabel = segoc.spellSpeed?.let { "SP$it " }.orEmpty()
                StatusChip(
                    label = "$speedLabel${segoc.effectType.name}",
                    tone = if (segoc.missedTimingRisk) StatusTone.Warning else StatusTone.Neutral,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowCatalogScreen(
    format: GameFormat,
    loading: Boolean,
    summaries: List<FlowSummary>,
    linkedIds: Set<String>,
    decks: List<com.ygochecker.core.model.Decklist>,
    focusDeckId: Long?,
    kindFilter: FlowKind?,
    onKindFilter: (FlowKind?) -> Unit,
    onFocusDeck: (Long?) -> Unit,
    onOpen: (String) -> Unit,
    onBackToCoach: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space2),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackToCoach) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(stringResource(DesignR.string.flow_back_to_coach), style = MaterialTheme.typography.labelLarge)
        }
        ThemedScreenHeader(
            title = stringResource(DesignR.string.flow_title),
            subtitle = stringResource(DesignR.string.flow_subtitle),
        )
        when {
            loading && summaries.isEmpty() -> {
                Text(
                    text = stringResource(DesignR.string.flow_loading),
                    modifier = Modifier.padding(DuelSpacing.space4),
                )
            }
            summaries.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.AccountTree,
                    title = stringResource(DesignR.string.flow_empty_title),
                    body = stringResource(DesignR.string.flow_empty_body),
                    modifier = Modifier
                        .weight(1f)
                        .padding(DuelSpacing.space4),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(DuelSpacing.space4),
                    verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                    modifier = Modifier.weight(1f),
                ) {
                    if (decks.isNotEmpty()) {
                        item(key = "deck-focus") {
                            Text(
                                text = stringResource(DesignR.string.flow_deck_links_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                decks.take(8).forEach { deck ->
                                    FilterChip(
                                        selected = focusDeckId == deck.id,
                                        onClick = { onFocusDeck(deck.id) },
                                        label = { Text(deck.name) },
                                    )
                                }
                            }
                            if (linkedIds.isNotEmpty()) {
                                Text(
                                    text = stringResource(DesignR.string.flow_linked_count, linkedIds.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    item(key = "filters") {
                        Text(
                            text = stringResource(DesignR.string.flow_format_hint, format.id.uppercase()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            FlowKind.entries.forEach { kind ->
                                FilterChip(
                                    selected = kindFilter == kind,
                                    onClick = { onKindFilter(kind) },
                                    label = { Text(kindLabel(kind)) },
                                )
                            }
                        }
                    }
                    items(summaries, key = { it.id }) { summary ->
                        FlowCard(
                            summary = summary,
                            linked = summary.id in linkedIds,
                            onClick = { onOpen(summary.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowCard(summary: FlowSummary, linked: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(DuelSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = summary.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(kindLabel(summary.kind), StatusTone.Info)
                summary.archetype?.let { StatusChip(it, StatusTone.Neutral) }
                if (linked) {
                    StatusChip(stringResource(DesignR.string.flow_linked_badge), StatusTone.Success)
                }
                StatusChip(
                    stringResource(
                        DesignR.string.flow_metrics_chip,
                        summary.metrics.steps,
                        summary.metrics.handCost,
                    ),
                    StatusTone.Neutral,
                )
            }
            if (summary.roleTags.isNotEmpty()) {
                Text(
                    text = summary.roleTags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FlowRehearsalScreen(
    graph: FlowGraph,
    engine: FlowRehearsalEngine,
    timingError: Boolean,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onAdvanceDefault: () -> Unit,
    onAdvanceFailTiming: () -> Unit,
    onChoose: (FlowEdge) -> Unit,
) {
    val node = engine.current
    val options = engine.options()
    val needsTiming = node.timingWindow == FlowRehearsalEngine.TIMING_HAND_DESTRUCTION_CL1 ||
        node.timingWindow == FlowRehearsalEngine.TIMING_CHAIN_LINK_1_ONLY

    Column(Modifier.fillMaxSize()) {
        ThemedScreenHeader(
            title = graph.title,
            subtitle = graph.archetype,
            actions = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
            modifier = Modifier.weight(1f),
        ) {
            item {
                SettingsGroup(title = stringResource(DesignR.string.flow_step_title)) {
                    Text(text = node.title, style = MaterialTheme.typography.titleLarge)
                    node.body?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        node.zoneHint?.let { StatusChip(it, StatusTone.Info) }
                        node.timingWindow?.let { StatusChip(it, StatusTone.Warning) }
                    }
                }
            }
            if (timingError) {
                item {
                    StatusChip(stringResource(DesignR.string.flow_timing_fail), StatusTone.Error)
                }
            }
            item {
                SettingsGroup(title = stringResource(DesignR.string.flow_branches_title)) {
                    if (engine.finished) {
                        Text(text = stringResource(DesignR.string.flow_finished))
                        Button(onClick = onRestart, modifier = Modifier.padding(top = 8.dp)) {
                            Text(text = stringResource(DesignR.string.flow_restart))
                        }
                    } else if (options.size <= 1) {
                        Button(onClick = onAdvanceDefault, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = options.firstOrNull()?.label
                                    ?: stringResource(DesignR.string.flow_next),
                            )
                        }
                        if (needsTiming) {
                            OutlinedButton(
                                onClick = onAdvanceFailTiming,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(text = stringResource(DesignR.string.flow_try_cl2))
                            }
                        }
                    } else {
                        options.forEach { edge ->
                            if (edge.isDefault) {
                                Button(
                                    onClick = { onChoose(edge) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                ) { Text(text = edge.label) }
                            } else {
                                OutlinedButton(
                                    onClick = { onChoose(edge) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                ) { Text(text = edge.label) }
                            }
                        }
                        if (needsTiming) {
                            Text(
                                text = stringResource(DesignR.string.flow_timing_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(
                        DesignR.string.flow_metrics_detail,
                        graph.metrics.steps,
                        graph.metrics.handCost,
                        graph.metrics.fieldCost,
                        graph.metrics.risk.name,
                        graph.metrics.payoff.name,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun kindLabel(kind: FlowKind): String = when (kind) {
    FlowKind.OPENING -> stringResource(DesignR.string.flow_kind_opening)
    FlowKind.BAIT_REACTION -> stringResource(DesignR.string.flow_kind_bait)
    FlowKind.ARTIFACT_ENGINE -> stringResource(DesignR.string.flow_kind_artifact)
    FlowKind.TRAP_LINE -> stringResource(DesignR.string.flow_kind_trap)
}
