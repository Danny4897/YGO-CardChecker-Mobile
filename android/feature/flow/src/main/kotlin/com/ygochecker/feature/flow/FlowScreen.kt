package com.ygochecker.feature.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.ygochecker.core.designsystem.effectMechanicLabel
import com.ygochecker.core.domain.EvaluateDeckLegality
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.GetEffectScripts
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.ObserveOfflinePack
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.GameFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class FlowValidation(
    val starters: List<String> = emptyList(),
    val extenders: List<String> = emptyList(),
    val handtraps: List<String> = emptyList(),
    val unscripted: List<String> = emptyList(),
    val legalityIssues: List<String> = emptyList(),
    val comboByTag: Map<String, List<String>> = emptyMap(),
    val scriptHits: Int = 0,
    val uniqueCards: Int = 0,
)

data class TestHandState(
    val deck: List<DeckCard> = emptyList(),
    val hand: List<DeckCard> = emptyList(),
    val field: List<DeckCard> = emptyList(),
    val gy: List<DeckCard> = emptyList(),
)

@HiltViewModel
class FlowViewModel @Inject constructor(
    listDecks: ListDecklists,
    formatPreference: FormatPreference,
    observePack: ObserveOfflinePack,
    private val legality: EvaluateDeckLegality,
    private val scripts: GetEffectScripts,
) : ViewModel() {
    val decks = listDecks.invoke().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val format = formatPreference.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameFormat.TCG)
    val pack = observePack.status().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.ygochecker.core.model.OfflinePackStatus(0, false, 0, null, null),
    )

    var selectedId by mutableStateOf<Long?>(null)
        private set
    var validation by mutableStateOf<FlowValidation?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var testHand by mutableStateOf(TestHandState())
        private set

    fun select(deck: Decklist) {
        selectedId = deck.id
        resetTestHand(deck)
        viewModelScope.launch {
            loading = true
            validation = validate(deck, format.value)
            loading = false
        }
    }

    fun drawOpeningHand(deck: Decklist) {
        val pool = expandMain(deck).shuffled(Random.Default)
        val hand = pool.take(5)
        testHand = TestHandState(deck = pool.drop(5), hand = hand, field = emptyList(), gy = emptyList())
    }

    fun mulligan(deck: Decklist) = drawOpeningHand(deck)

    fun playFromHand(index: Int) {
        val card = testHand.hand.getOrNull(index) ?: return
        testHand = testHand.copy(
            hand = testHand.hand.filterIndexed { i, _ -> i != index },
            field = testHand.field + card,
        )
    }

    fun handToGy(index: Int) {
        val card = testHand.hand.getOrNull(index) ?: return
        testHand = testHand.copy(
            hand = testHand.hand.filterIndexed { i, _ -> i != index },
            gy = testHand.gy + card,
        )
    }

    private fun resetTestHand(deck: Decklist) {
        testHand = TestHandState(deck = expandMain(deck))
    }

    private fun expandMain(deck: Decklist): List<DeckCard> =
        deck.cards
            .filter { it.section == DeckSection.MAIN }
            .flatMap { entry -> List(entry.quantity) { entry.copy(quantity = 1) } }

    fun ensureSelection(decks: List<Decklist>, format: GameFormat) {
        if (decks.isEmpty()) {
            selectedId = null
            validation = null
            testHand = TestHandState()
            return
        }
        val current = decks.firstOrNull { it.id == selectedId } ?: decks.first()
        if (selectedId != current.id) {
            selectedId = current.id
            resetTestHand(current)
        }
        viewModelScope.launch {
            loading = true
            validation = validate(current, format)
            loading = false
        }
    }

    private suspend fun validate(deck: Decklist, format: GameFormat): FlowValidation {
        val ids = deck.cards.map { it.card.id }.distinct()
        val byId = if (ids.isEmpty()) {
            emptyMap()
        } else {
            scripts.invoke(ids).associateBy(EffectScriptSummary::cardId)
        }
        val starters = linkedSetOf<String>()
        val extenders = linkedSetOf<String>()
        val handtraps = linkedSetOf<String>()
        val unscripted = linkedSetOf<String>()
        val combo = linkedMapOf<String, MutableList<String>>()
        var hits = 0
        deck.cards.groupBy { it.card.id }.forEach { (id, entries) ->
            val name = entries.first().card.name
            val script = byId[id]
            val roles = script?.roles.orEmpty().map { it.lowercase() }
            val tags = script?.tags.orEmpty()
            if (script != null && (roles.isNotEmpty() || tags.isNotEmpty())) hits += 1
            tags.forEach { tag -> combo.getOrPut(tag) { mutableListOf() }.add(name) }
            val inferredExtender = tags.any {
                it.contains("ss_from") || it == "special_summons" || it == "searches_deck" || it == "revives_from_gy"
            }
            val inferredStarter = tags.any {
                it == "searches_deck" || it == "ss_from_deck" || it == "ss_from_hand"
            }
            val inferredHandtrap = tags.contains("hand_trap") || tags.contains("quick_effect") ||
                tags.contains("negates") || tags.contains("discards")
            when {
                script == null || (roles.isEmpty() && tags.isEmpty()) -> unscripted += name
                else -> {
                    if (roles.any { it.contains("starter") } || inferredStarter) starters += name
                    if (roles.any { it.contains("extender") } || inferredExtender) extenders += name
                    if (roles.any {
                            it.contains("handtrap") || it.contains("hand-trap") || it.contains("hand_trap")
                        } || inferredHandtrap
                    ) {
                        handtraps += name
                    }
                }
            }
        }
        val issues = legality.invoke(deck, format).cards
            .filter { !it.isLegal }
            .map { card ->
                val name = deck.cards.firstOrNull { it.card.id == card.cardId }?.card?.name
                    ?: card.cardId.toString()
                "$name (${card.currentCopies}/${card.maxCopies})"
            }
        return FlowValidation(
            starters = starters.toList(),
            extenders = extenders.toList(),
            handtraps = handtraps.toList(),
            unscripted = unscripted.toList(),
            legalityIssues = issues,
            comboByTag = combo.mapValues { it.value.distinct() }.filterValues { it.isNotEmpty() },
            scriptHits = hits,
            uniqueCards = ids.size,
        )
    }
}

@Composable
fun FlowRoute(vm: FlowViewModel = hiltViewModel()) {
    // Engine UI parked — Coming Soon until we redesign Flow.
    com.ygochecker.core.designsystem.ComingSoonScreen(
        title = stringResource(DesignR.string.flow_title),
        body = stringResource(DesignR.string.flow_coming_soon_body),
    )
}

@Composable
@Suppress("unused")
private fun FlowRouteLegacy(vm: FlowViewModel = hiltViewModel()) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val pack by vm.pack.collectAsStateWithLifecycle()
    LaunchedEffect(decks, format) {
        vm.ensureSelection(decks, format)
    }
    Column(Modifier.fillMaxSize()) {
        ThemedScreenHeader(
            stringResource(DesignR.string.flow_title),
            stringResource(DesignR.string.flow_subtitle),
        )
        if (decks.isEmpty()) {
            EmptyState(
                icon = Icons.Default.AccountTree,
                title = stringResource(DesignR.string.flow_empty_deck),
                body = stringResource(DesignR.string.decks_empty_body),
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                stringResource(DesignR.string.flow_pick_deck),
                modifier = Modifier.padding(horizontal = DuelSpacing.space4),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                decks.forEach { deck ->
                    FilterChip(
                        selected = deck.id == vm.selectedId,
                        onClick = { vm.select(deck) },
                        label = { Text(deck.name) },
                    )
                }
            }
            if (pack.effectScriptCount < 100) {
                Text(
                    stringResource(DesignR.string.flow_scripts_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space1),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(DuelSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
            ) {
                item(key = "combo-roadmap") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(DuelSpacing.space3),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(DesignR.string.flow_combo_roadmap_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(DesignR.string.flow_combo_roadmap_body),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item(key = "test-hand") {
                    val deck = decks.firstOrNull { it.id == vm.selectedId } ?: decks.first()
                    TestHandBoard(
                        state = vm.testHand,
                        onDraw = { vm.drawOpeningHand(deck) },
                        onMulligan = { vm.mulligan(deck) },
                        onPlay = vm::playFromHand,
                        onGy = vm::handToGy,
                    )
                }
                item(key = "deck-summary") {
                    val deck = decks.firstOrNull { it.id == vm.selectedId } ?: decks.first()
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(DuelSpacing.space3),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(deck.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    DesignR.string.decks_section_counts,
                                    deck.cards.filter { it.section == DeckSection.MAIN }.sumOf { it.quantity },
                                    deck.cards.filter { it.section == DeckSection.EXTRA }.sumOf { it.quantity },
                                    deck.cards.filter { it.section == DeckSection.SIDE }.sumOf { it.quantity },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            vm.validation?.let { result ->
                                Text(
                                    stringResource(
                                        DesignR.string.flow_script_coverage,
                                        result.scriptHits,
                                        result.uniqueCards,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
                item(key = "validation") {
                    val result = vm.validation
                    if (vm.loading && result == null) {
                        Text(
                            stringResource(DesignR.string.flow_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else if (result != null) {
                        SettingsGroup(title = stringResource(DesignR.string.flow_roles_title)) {
                            RoleBlock(stringResource(DesignR.string.flow_starters), result.starters)
                            RoleBlock(stringResource(DesignR.string.flow_extenders), result.extenders)
                            RoleBlock(stringResource(DesignR.string.flow_handtraps), result.handtraps)
                            RoleBlock(stringResource(DesignR.string.flow_unscripted), result.unscripted)
                            if (result.comboByTag.isNotEmpty()) {
                                Text(
                                    stringResource(DesignR.string.flow_combo_tags),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                result.comboByTag.entries
                                    .sortedByDescending { it.value.size }
                                    .take(12)
                                    .forEach { (tag, names) ->
                                        RoleBlock(effectMechanicLabel(tag), names)
                                    }
                            }
                            Text(
                                stringResource(DesignR.string.flow_legality),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (result.legalityIssues.isEmpty()) {
                                StatusChip(stringResource(DesignR.string.flow_ok), StatusTone.Success)
                            } else {
                                result.legalityIssues.forEach { StatusChip(it, StatusTone.Error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestHandBoard(
    state: TestHandState,
    onDraw: () -> Unit,
    onMulligan: () -> Unit,
    onPlay: (Int) -> Unit,
    onGy: (Int) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(DuelSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
        ) {
            Text(stringResource(DesignR.string.flow_test_hand), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(DesignR.string.flow_engine_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = onDraw,
                    label = { Text(stringResource(DesignR.string.flow_draw_hand)) },
                )
                FilterChip(
                    selected = false,
                    onClick = onMulligan,
                    label = { Text(stringResource(DesignR.string.flow_mulligan)) },
                )
            }
            // MDPRO-like vertical zones: Deck → Field → Hand → GY
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(DuelSpacing.space3),
                    verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
                ) {
                    ZoneLabel(stringResource(DesignR.string.flow_zone_deck), state.deck.size)
                    ZoneLabel(stringResource(DesignR.string.flow_zone_field), state.field.size)
                    if (state.field.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.field.forEach { MiniCardChip(it.card.name) }
                        }
                    }
                    Text(stringResource(DesignR.string.flow_zone_hand), style = MaterialTheme.typography.titleSmall)
                    if (state.hand.isEmpty()) {
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.hand.forEachIndexed { index, card ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        card.card.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        stringResource(DesignR.string.flow_play_to_field),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onPlay(index) },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        stringResource(DesignR.string.flow_send_gy),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clickable { onGy(index) },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                    ZoneLabel(stringResource(DesignR.string.flow_zone_gy), state.gy.size)
                    if (state.gy.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.gy.forEach { MiniCardChip(it.card.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneLabel(title: String, count: Int) {
    Text("$title · $count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun MiniCardChip(name: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(name, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleBlock(title: String, names: List<String>) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (names.isEmpty()) {
        Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = DuelSpacing.space2),
        ) {
            names.take(24).forEach { name ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
