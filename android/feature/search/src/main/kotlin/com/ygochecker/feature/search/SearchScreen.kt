package com.ygochecker.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.ygochecker.core.designsystem.CardDetailSheet
import com.ygochecker.core.designsystem.CardDetailState
import com.ygochecker.core.designsystem.CollectionPickOption
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.EmptyState
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.SaveToCollectionDialog
import com.ygochecker.core.designsystem.StatusChip
import com.ygochecker.core.designsystem.StatusTone
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.designsystem.effectMechanicLabel
import com.ygochecker.core.domain.CreateDecklist
import com.ygochecker.core.domain.EvaluateDeckLegality
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.GetDecklist
import com.ygochecker.core.domain.GetEffectScript
import com.ygochecker.core.domain.GetLocalizedCard
import com.ygochecker.core.domain.GetRelatedCards
import com.ygochecker.core.domain.LanguagePreference
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.domain.RandomPlayableCard
import com.ygochecker.core.domain.SearchCards
import com.ygochecker.core.domain.SetCardQuantity
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.EffectMechanicTags
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.SearchFilters
import com.ygochecker.core.model.isExtraDeckType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCards: SearchCards,
    private val randomPlayable: RandomPlayableCard,
    private val decks: ListDecklists,
    private val getDeck: GetDecklist,
    private val createDeck: CreateDecklist,
    private val setCard: SetCardQuantity,
    private val formats: FormatPreference,
    private val languages: LanguagePreference,
    private val legality: EvaluateDeckLegality,
    private val getScript: GetEffectScript,
    private val getRelated: GetRelatedCards,
    private val getLocalized: GetLocalizedCard,
    private val profile: ProfileRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filters = MutableStateFlow(SearchFilters())
    val format = formats.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameFormat.TCG)
    val language = languages.values.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppLanguage.ENGLISH,
    )
    val collections = profile.observeCollections().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val results = combine(query.debounce(250), language, formats.values, filters) { q, _, fmt, f ->
        Triple(q.trim(), fmt, f)
    }.flatMapLatest { (q, fmt, f) ->
        searchCards.invoke(q, fmt, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _noticeKey = MutableStateFlow<SearchNotice?>(null)
    val notice = _noticeKey

    var detail by mutableStateOf<CardDetailState?>(null)
        private set

    /** Keeps the random (or last opened) card visible in the list after closing the sheet. */
    var pinnedCard by mutableStateOf<Card?>(null)
        private set

    init {
        viewModelScope.launch {
            combine(formats.values, languages.values) { fmt, lang -> fmt to lang }
                .collect { (fmt, lang) -> refreshOpenDetail(fmt, lang) }
        }
    }

    fun maxCopies(cardId: Int) = legality.maxCopies(cardId, format.value)

    fun setLanguage(value: AppLanguage) = viewModelScope.launch { languages.set(value) }

    fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        filters.value = transform(filters.value)
    }

    fun clearQuery() {
        query.value = ""
        pinnedCard = null
    }

    fun onQueryChange(value: String) {
        query.value = value
        val pinned = pinnedCard
        if (pinned != null && value.isNotBlank() &&
            !pinned.name.contains(value.trim(), ignoreCase = true) &&
            !value.contains(pinned.name, ignoreCase = true)
        ) {
            pinnedCard = null
        }
        if (value.isBlank()) pinnedCard = null
    }

    fun pickRandom() = viewModelScope.launch {
        val card = randomPlayable.invoke(format.value).first()
        if (card != null) {
            pinnedCard = card
            query.value = card.name
            openDetail(card)
        }
    }

    fun openDetail(card: Card) = viewModelScope.launch {
        pinnedCard = card
        val fmt = format.value
        val lang = language.value
        val existing = decks.invoke().first().firstOrNull()
        val deck = existing?.let { getDeck.invoke(it.id).first() }
        val section = if (isExtraDeckType(card.type)) DeckSection.EXTRA else DeckSection.MAIN
        val qty = deck?.cards
            ?.filter { it.card.id == card.id && it.section == section }
            ?.sumOf { it.quantity }
            ?: 0
        val localized = getLocalized.invoke(card.id, lang) ?: card
        val script = getScript.invoke(card.id)
        detail = CardDetailState(
            card = localized,
            maxCopies = legality.maxCopies(card.id, fmt),
            inDeckQuantity = qty,
            roles = script?.roles.orEmpty(),
            effectTags = script?.tags.orEmpty(),
            related = getRelated.invoke(card.id, fmt, 36),
            formatLabel = fmt.id.uppercase(),
        )
    }

    fun openRelated(ref: RelatedCardRef) = viewModelScope.launch {
        val card = getLocalized.invoke(ref.id, language.value)
            ?: Card(id = ref.id, name = ref.name, type = "", imageUrl = ref.imageUrl)
        openDetail(card)
    }

    fun addRelated(ref: RelatedCardRef) = viewModelScope.launch {
        val card = getLocalized.invoke(ref.id, language.value)
            ?: Card(id = ref.id, name = ref.name, type = "", imageUrl = ref.imageUrl)
        quickAdd(card)
    }

    fun closeDetail() {
        detail = null
    }

    fun saveToCollection(collectionId: Long, cardId: Int) = viewModelScope.launch {
        if (collectionId <= 0) {
            val id = profile.createCollection("Inbox")
            profile.addCardToCollection(id, cardId)
        } else {
            profile.addCardToCollection(collectionId, cardId)
        }
    }

    fun createCollectionAndSave(name: String, cardId: Int) = viewModelScope.launch {
        val id = profile.createCollection(name)
        profile.addCardToCollection(id, cardId)
    }

    private suspend fun refreshOpenDetail(fmt: GameFormat, lang: AppLanguage) {
        val state = detail ?: return
        val localized = getLocalized.invoke(state.card.id, lang) ?: state.card
        val script = getScript.invoke(state.card.id)
        detail = state.copy(
            card = localized,
            maxCopies = legality.maxCopies(state.card.id, fmt),
            roles = script?.roles.orEmpty(),
            effectTags = script?.tags.orEmpty(),
            related = getRelated.invoke(state.card.id, fmt, 24),
            formatLabel = fmt.id.uppercase(),
        )
    }

    fun adjustFromDetail(delta: Int) = viewModelScope.launch {
        val state = detail ?: return@launch
        val card = state.card
        val section = if (isExtraDeckType(card.type)) DeckSection.EXTRA else DeckSection.MAIN
        val existing = decks.invoke().first().firstOrNull()
        val deckId = existing?.id ?: createDeck.invoke("My deck")
        val next = (state.inDeckQuantity + delta).coerceAtLeast(0)
        val max = legality.maxCopies(card.id, format.value)
        if (delta > 0 && max <= 0) {
            _noticeKey.value = SearchNotice.Forbidden(card.name)
            return@launch
        }
        if (delta > 0 && state.inDeckQuantity >= max) {
            _noticeKey.value = SearchNotice.AtLimit(card.name, max)
            return@launch
        }
        setCard.invoke(deckId, card, next.coerceAtMost(max.coerceAtLeast(0)), section)
        if (delta > 0) _noticeKey.value = SearchNotice.Added(card.name)
        detail = state.copy(inDeckQuantity = next.coerceAtMost(max.coerceAtLeast(0)))
    }

    fun quickAdd(card: Card) = viewModelScope.launch {
        val section = if (isExtraDeckType(card.type)) DeckSection.EXTRA else DeckSection.MAIN
        val existing = decks.invoke().first().firstOrNull()
        val deckId = existing?.id ?: createDeck.invoke("My deck")
        val deck = getDeck.invoke(deckId).first()
        val current = deck?.cards
            ?.filter { it.card.id == card.id && it.section == section }
            ?.sumOf { it.quantity }
            ?: 0
        val max = legality.maxCopies(card.id, format.value)
        if (max <= 0) {
            _noticeKey.value = SearchNotice.Forbidden(card.name)
            return@launch
        }
        if (current >= max) {
            _noticeKey.value = SearchNotice.AtLimit(card.name, max)
            return@launch
        }
        setCard.invoke(deckId, card, current + 1, section)
        _noticeKey.value = SearchNotice.Added(card.name)
        detail?.takeIf { it.card.id == card.id }?.let {
            detail = it.copy(inDeckQuantity = current + 1)
        }
    }

    fun consumeNotice() {
        _noticeKey.value = null
    }
}

sealed class SearchNotice {
    data class Added(val name: String) : SearchNotice()
    data class AtLimit(val name: String, val max: Int) : SearchNotice()
    data class Forbidden(val name: String) : SearchNotice()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchRoute(vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val message = when (val n = notice) {
        is SearchNotice.Added -> stringResource(DesignR.string.search_card_added, n.name)
        is SearchNotice.AtLimit -> stringResource(DesignR.string.search_at_limit, n.name, n.max)
        is SearchNotice.Forbidden -> stringResource(DesignR.string.search_forbidden, n.name)
        null -> null
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.consumeNotice()
        }
    }

    val language by vm.language.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    var filtersExpanded by remember { mutableStateOf(false) }
    val displayResults = remember(results, vm.pinnedCard) {
        val pinned = vm.pinnedCard
        when {
            pinned == null -> results
            results.any { it.id == pinned.id } -> {
                listOf(results.first { it.id == pinned.id }) + results.filterNot { it.id == pinned.id }
            }
            else -> listOf(pinned) + results
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ThemedScreenHeader(
                title = stringResource(DesignR.string.search_title),
                subtitle = stringResource(DesignR.string.search_subtitle),
                actions = {
                    FilledTonalButton(
                        onClick = {
                            vm.setLanguage(
                                if (language == AppLanguage.ITALIAN) AppLanguage.ENGLISH
                                else AppLanguage.ITALIAN,
                            )
                        },
                    ) {
                        Text(
                            text = if (language == AppLanguage.ITALIAN) "IT" else "EN",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { vm.onQueryChange(it) },
                singleLine = true,
                label = { Text(stringResource(DesignR.string.search_card_name)) },
                placeholder = { Text(stringResource(DesignR.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { filtersExpanded = true }) {
                            BadgedBox(
                                badge = {
                                    if (!filters.isDefault) {
                                        Badge()
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(DesignR.string.search_filters),
                                )
                            }
                        }
                        IconButton(onClick = vm::pickRandom) {
                            Icon(
                                Icons.Default.Casino,
                                contentDescription = stringResource(DesignR.string.search_random),
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = vm::clearQuery) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(DesignR.string.search_clear_query),
                                )
                            }
                        }
                    }
                },
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DuelSpacing.space4),
            )
            if (filtersExpanded) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { filtersExpanded = false },
                    sheetState = sheetState,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = DuelSpacing.space4)
                            .padding(bottom = DuelSpacing.space4),
                    ) {
                        Text(
                            stringResource(DesignR.string.search_filters),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(DesignR.string.search_filter_sheet_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = DuelSpacing.space1, bottom = DuelSpacing.space3),
                        )
                        SearchFiltersBar(
                            filters = filters,
                            onChange = vm::updateFilters,
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                        Spacer(Modifier.height(DuelSpacing.space3))
                        HorizontalDivider()
                        Spacer(Modifier.height(DuelSpacing.space2))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
                        ) {
                            FilledTonalButton(
                                onClick = { vm.updateFilters { SearchFilters() } },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(DesignR.string.search_clear_filters))
                            }
                            FilledTonalButton(
                                onClick = { filtersExpanded = false },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(DesignR.string.search_filters_done))
                            }
                        }
                    }
                }
            }
            when {
                displayResults.isEmpty() && query.isBlank() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(DesignR.string.search_browse_empty_title),
                    body = stringResource(DesignR.string.search_browse_empty_body),
                    modifier = Modifier.weight(1f),
                )
                displayResults.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(DesignR.string.search_no_results, query),
                    body = stringResource(DesignR.string.search_local_only),
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = DuelSpacing.space4,
                        vertical = DuelSpacing.space3,
                    ),
                    verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
                ) {
                    if (query.isBlank() && vm.pinnedCard == null) {
                        item(key = "browse-hint") {
                            Text(
                                stringResource(
                                    DesignR.string.search_browse_playable,
                                    format.id.uppercase(),
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = DuelSpacing.space2),
                            )
                        }
                    }
                    items(displayResults, key = Card::id, contentType = { "card" }) { card ->
                        SearchCardRow(
                            card = card,
                            maximum = vm.maxCopies(card.id),
                            formatId = format.id,
                            onOpen = { vm.openDetail(card) },
                            onAdd = { vm.quickAdd(card) },
                        )
                    }
                }
            }
        }
    }
    var saveCollectionOpen by remember { mutableStateOf(false) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    vm.detail?.let { state ->
        CardDetailSheet(
            state = state,
            onDismiss = vm::closeDetail,
            onDecrease = { vm.adjustFromDetail(-1) },
            onIncrease = { vm.adjustFromDetail(1) },
            onRelatedOpen = vm::openRelated,
            onRelatedAdd = vm::addRelated,
            onSaveToCollection = { saveCollectionOpen = true },
        )
        if (saveCollectionOpen) {
            SaveToCollectionDialog(
                collections = collections.map { CollectionPickOption(it.id, it.name) },
                onDismiss = { saveCollectionOpen = false },
                onPick = { id ->
                    vm.saveToCollection(id, state.card.id)
                    saveCollectionOpen = false
                },
                onCreateAndSave = { name ->
                    vm.createCollectionAndSave(name, state.card.id)
                    saveCollectionOpen = false
                },
            )
        }
    }
}

@Composable
private fun SearchFiltersBar(
    filters: SearchFilters,
    onChange: ((SearchFilters) -> SearchFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3)) {
        FilterSection(stringResource(DesignR.string.search_filter_section_type)) {
            IconFilterChip(
                selected = filters.playableOnly,
                onClick = { onChange { it.copy(playableOnly = !it.playableOnly) } },
                label = stringResource(DesignR.string.search_filter_playable),
                icon = Icons.Default.Security,
            )
            typeChip(filters, "Monster", DesignR.string.search_filter_monster, Icons.Default.PersonAdd, onChange)
            typeChip(filters, "Spell", DesignR.string.search_filter_spell, Icons.Default.AutoFixHigh, onChange)
            typeChip(filters, "Trap", DesignR.string.search_filter_trap, Icons.Default.Inventory2, onChange)
            typeChip(filters, "EXTRA", DesignR.string.search_filter_extra, Icons.Default.AutoAwesome, onChange)
        }
        FilterSection(stringResource(DesignR.string.search_filter_section_level)) {
            (1..12).forEach { level ->
                IconFilterChip(
                    selected = filters.levelMin == level && filters.levelMax == level,
                    onClick = {
                        onChange {
                            if (it.levelMin == level && it.levelMax == level) {
                                it.copy(levelMin = null, levelMax = null)
                            } else {
                                it.copy(levelMin = level, levelMax = level)
                            }
                        }
                    },
                    label = "$level",
                    icon = Icons.Default.Star,
                )
            }
        }
        FilterSection(stringResource(DesignR.string.search_filter_section_atk)) {
            StatExactField(
                label = stringResource(DesignR.string.search_filter_atk_exact),
                value = filters.atkMin?.takeIf { it == filters.atkMax }?.toString().orEmpty(),
                onValueChange = { raw ->
                    onChange { f ->
                        val n = raw.filter(Char::isDigit).toIntOrNull()
                        if (n == null) f.copy(atkMin = null, atkMax = null)
                        else f.copy(atkMin = n, atkMax = n)
                    }
                },
            )
        }
        FilterSection(stringResource(DesignR.string.search_filter_section_def)) {
            StatExactField(
                label = stringResource(DesignR.string.search_filter_def_exact),
                value = filters.defMin?.takeIf { it == filters.defMax }?.toString().orEmpty(),
                onValueChange = { raw ->
                    onChange { f ->
                        val n = raw.filter(Char::isDigit).toIntOrNull()
                        if (n == null) f.copy(defMin = null, defMax = null)
                        else f.copy(defMin = n, defMax = n)
                    }
                },
            )
        }
        Text(
            stringResource(DesignR.string.search_filter_stats_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Effect types first-class (GY SS, discard, search…) — not starter/extender roles.
        FilterSection(stringResource(DesignR.string.search_filter_section_effect)) {
            EffectMechanicTags.FILTERABLE.forEach { tag ->
                IconFilterChip(
                    selected = filters.effectTag == tag,
                    onClick = {
                        onChange { it.copy(effectTag = if (it.effectTag == tag) "" else tag) }
                    },
                    label = effectMechanicLabel(tag),
                    icon = effectTagIcon(tag),
                )
            }
        }
        FilterSection(stringResource(DesignR.string.search_filter_section_attr)) {
            ATTRIBUTE_FILTERS.forEach { (attr, icon) ->
                IconFilterChip(
                    selected = filters.attribute.equals(attr, ignoreCase = true),
                    onClick = {
                        onChange {
                            it.copy(attribute = if (it.attribute.equals(attr, ignoreCase = true)) "" else attr)
                        }
                    },
                    label = attr,
                    icon = icon,
                )
            }
        }
        FilterSection(stringResource(DesignR.string.search_filter_section_race)) {
            RACE_FILTERS.forEach { race ->
                IconFilterChip(
                    selected = filters.race.equals(race, ignoreCase = true),
                    onClick = {
                        onChange {
                            it.copy(race = if (it.race.equals(race, ignoreCase = true)) "" else race)
                        }
                    },
                    label = race,
                    icon = Icons.Default.Shield,
                )
            }
        }
        FilterSection(stringResource(DesignR.string.search_filter_section_era)) {
            IconFilterChip(
                selected = filters.yearMax == 2014,
                onClick = {
                    onChange {
                        if (it.yearMax == 2014) it.copy(yearMax = null) else it.copy(yearMax = 2014)
                    }
                },
                label = stringResource(DesignR.string.search_filter_year_hat),
                icon = Icons.Default.AutoAwesome,
            )
        }
        if (filters.hasFacets || !filters.playableOnly) {
            FilledTonalButton(onClick = { onChange { SearchFilters() } }) {
                Text(stringResource(DesignR.string.search_clear_filters))
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun IconFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun typeChip(
    filters: SearchFilters,
    needle: String,
    labelRes: Int,
    icon: ImageVector,
    onChange: ((SearchFilters) -> SearchFilters) -> Unit,
) {
    IconFilterChip(
        selected = filters.typeNeedle == needle,
        onClick = {
            onChange { it.copy(typeNeedle = if (it.typeNeedle == needle) "" else needle) }
        },
        label = stringResource(labelRes),
        icon = icon,
    )
}

@Composable
private fun StatExactField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(0.55f),
    )
}

private fun effectTagIcon(tag: String): ImageVector = when (tag) {
    "gy_effect", "sends_to_gy", "hand_to_gy", "revives_from_gy", "ss_from_gy" -> Icons.Default.Inventory2
    "ss_from_hand", "ss_from_deck", "ss_from_extra", "special_summons" -> Icons.Default.PersonAdd
    "searches_deck" -> Icons.Default.Search
    "draw" -> Icons.Default.AutoAwesome
    "destroys", "banishes" -> Icons.Default.DeleteSweep
    "negates" -> Icons.Default.Security
    "discards", "mills" -> Icons.Default.DeleteSweep
    "quick_effect" -> Icons.Default.FlashOn
    "trigger_effect" -> Icons.Default.AutoFixHigh
    "changes_level" -> Icons.Default.Star
    else -> Icons.Default.AutoFixHigh
}

private val ATTRIBUTE_FILTERS = listOf(
    "DARK" to Icons.Default.DarkMode,
    "LIGHT" to Icons.Default.WbSunny,
    "EARTH" to Icons.Default.Terrain,
    "WATER" to Icons.Default.WaterDrop,
    "FIRE" to Icons.Default.LocalFireDepartment,
    "WIND" to Icons.Default.Air,
    "DIVINE" to Icons.Default.AutoAwesome,
)

private val RACE_FILTERS = listOf(
    "Warrior", "Spellcaster", "Dragon", "Machine", "Fiend", "Zombie",
    "Beast", "Winged Beast", "Aqua", "Pyro", "Thunder", "Rock",
    "Plant", "Insect", "Dinosaur", "Sea Serpent", "Fish", "Psychic",
    "Wyrm", "Cyberse", "Illusion",
)

@Composable
private fun SearchCardRow(
    card: Card,
    maximum: Int,
    formatId: String,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    val addDescription = stringResource(DesignR.string.search_add_card, card.name)
    val context = LocalContext.current
    val imageRequest = remember(card.imageUrl) {
        ImageRequest.Builder(context)
            .data(card.imageUrl)
            .size(Size(104, 152))
            .crossfade(false)
            .build()
    }
    val (legalityLabel, tone) = when (maximum) {
        0 -> stringResource(DesignR.string.legality_forbidden) to StatusTone.Error
        1 -> stringResource(DesignR.string.legality_limited) to StatusTone.Warning
        2 -> stringResource(DesignR.string.legality_semi_limited) to StatusTone.Warning
        else -> stringResource(DesignR.string.legality_valid) to StatusTone.Success
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(DuelSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            Box(
                Modifier
                    .size(52.dp, 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(
                    label = if (maximum <= 0) {
                        stringResource(DesignR.string.legality_forbidden)
                    } else {
                        "$legalityLabel · ${formatId.uppercase()}"
                    },
                    tone = tone,
                )
            }
            FilledTonalIconButton(onClick = onAdd, enabled = maximum > 0) {
                Icon(Icons.Default.Add, addDescription)
            }
        }
    }
}
