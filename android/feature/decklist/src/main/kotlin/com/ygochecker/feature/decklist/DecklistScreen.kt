package com.ygochecker.feature.decklist

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.designsystem.AttributeBadge
import com.ygochecker.core.designsystem.CardDetailSheet
import com.ygochecker.core.designsystem.CardDetailState
import com.ygochecker.core.designsystem.CollectionPickOption
import com.ygochecker.core.designsystem.ComboLinePreview
import com.ygochecker.core.designsystem.DeckMetaRow
import com.ygochecker.core.designsystem.DuelDeckForgeSplash
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.DuelWorkingBanner
import com.ygochecker.core.designsystem.EmptyState
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.SaveToCollectionDialog
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.designsystem.StatusChip
import com.ygochecker.core.designsystem.StatusTone
import com.ygochecker.core.designsystem.errorMessage
import com.ygochecker.core.designsystem.formatPriceEur
import com.ygochecker.core.domain.AnalyzeDeckCombos
import com.ygochecker.core.domain.BudgetSwapSuggestion
import com.ygochecker.core.domain.CompleteDeck
import com.ygochecker.core.domain.CreateDecklist
import com.ygochecker.core.domain.DeleteDecklist
import com.ygochecker.core.domain.EvaluateDeckLegality
import com.ygochecker.core.domain.ExportDeckToText
import com.ygochecker.core.domain.ExportDeckToYdk
import com.ygochecker.core.domain.ExportDeckToYdke
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.GenerateDeckFlows
import com.ygochecker.core.domain.GetDecklist
import com.ygochecker.core.domain.GetEffectScript
import com.ygochecker.core.domain.GetLocalizedCard
import com.ygochecker.core.domain.GetRelatedCards
import com.ygochecker.core.domain.ImportDeckFromText
import com.ygochecker.core.domain.ImportDeckFromYdk
import com.ygochecker.core.domain.ImportDeckFromYdke
import com.ygochecker.core.domain.LanguagePreference
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.PersistImportedDeck
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.domain.RenameDecklist
import com.ygochecker.core.domain.SetCardQuantity
import com.ygochecker.core.domain.SetDeckCovers
import com.ygochecker.core.domain.SetDeckPublic
import com.ygochecker.core.domain.SocialRepository
import com.ygochecker.core.domain.SuggestBudgetSwaps
import com.ygochecker.core.domain.SuggestCombosForCard
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.ComboActionKind
import com.ygochecker.core.model.ComboLineAdvice
import com.ygochecker.core.model.DeckCard
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.FormatExtraStaples
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.deckPresentationCards
import com.ygochecker.core.model.isExtraDeckType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckComboReportUi(
    val lines: List<ComboLinePreview>,
    val actionCards: List<RelatedCardRef>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel class DecklistViewModel @Inject constructor(
    listDecks: ListDecklists,
    private val getDeck: GetDecklist,
    private val createDeck: CreateDecklist,
    private val deleteDeck: DeleteDecklist,
    private val renameDeck: RenameDecklist,
    private val setQuantity: SetCardQuantity,
    private val setCovers: SetDeckCovers,
    private val setPublic: SetDeckPublic,
    private val social: SocialRepository,
    private val getScript: GetEffectScript,
    private val getLocalized: GetLocalizedCard,
    private val getRelated: GetRelatedCards,
    private val suggestCombos: SuggestCombosForCard,
    private val analyzeDeckCombos: AnalyzeDeckCombos,
    private val suggestBudgetSwaps: SuggestBudgetSwaps,
    private val completeDeckUseCase: CompleteDeck,
    private val generateDeckFlows: GenerateDeckFlows,
    private val importText: ImportDeckFromText,
    private val importYdke: ImportDeckFromYdke,
    private val importYdk: ImportDeckFromYdk,
    private val persistImported: PersistImportedDeck,
    private val exportText: ExportDeckToText,
    private val exportYdke: ExportDeckToYdke,
    private val exportYdk: ExportDeckToYdk,
    formatPreference: FormatPreference,
    languagePreference: LanguagePreference,
    private val evaluateLegality: EvaluateDeckLegality,
    private val profile: ProfileRepository,
) : ViewModel() {
    val decks = listDecks.invoke().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedId = MutableStateFlow<Long?>(null)
    val selected = selectedId.flatMapLatest { id -> id?.let(getDeck::invoke) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val format = formatPreference.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameFormat.TCG)
    private val language = languagePreference.values.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppLanguage.ENGLISH,
    )
    val collections = profile.observeCollections().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _io = MutableStateFlow(DeckIoUiState())
    val io = _io.asStateFlow()
    var detail by mutableStateOf<CardDetailState?>(null)
        private set
    var comboReport by mutableStateOf<DeckComboReportUi?>(null)
        private set
    var completeNotice by mutableStateOf<String?>(null)
        private set
    var completeBusy by mutableStateOf(false)
        private set
    var budgetSuggestions by mutableStateOf<List<BudgetSwapSuggestion>?>(null)
        private set
    var budgetBusy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            combine(formatPreference.values, languagePreference.values) { fmt, lang -> fmt to lang }
                .collect { (fmt, lang) -> refreshOpenDetail(fmt, lang) }
        }
    }

    fun select(id: Long?) { selectedId.value = id }
    fun create() = viewModelScope.launch { selectedId.value = createDeck.invoke("New deck") }
    fun delete(id: Long) = viewModelScope.launch { deleteDeck.invoke(id); selectedId.value = null }
    fun setVisibility(id: Long, isPublic: Boolean) = viewModelScope.launch {
        setPublic.invoke(id, isPublic)
        val deck = getDeck.invoke(id).first()
        if (deck != null) {
            val result = if (isPublic) social.publishDeck(deck) else social.unpublishDeck(id)
            if (result is AppResult.Err) {
                // Remote publish/unpublish failed — don't leave the local flag claiming a
                // state (e.g. "public") that was never actually reached on the server, or the
                // deck becomes an unreachable thread in Profile.
                setPublic.invoke(id, !isPublic)
                completeNotice = "err:${result.error.errorKey}"
            }
        }
    }
    fun clearCompleteNotice() { completeNotice = null }
    fun closeComboReport() { comboReport = null }
    fun closeBudgetSuggestions() { budgetSuggestions = null }
    fun suggestBudget() = viewModelScope.launch {
        val deck = selected.value ?: return@launch
        budgetBusy = true
        budgetSuggestions = suggestBudgetSwaps.invoke(deck.cards, format.value, maxSuggestions = 6)
        budgetBusy = false
    }
    fun applyBudgetSwap(suggestion: BudgetSwapSuggestion) = viewModelScope.launch {
        val id = selected.value?.id ?: return@launch
        val maxCopies = evaluateLegality.maxCopies(suggestion.alternative.id, format.value)
        val addQuantity = suggestion.expensive.quantity.coerceAtMost(maxCopies.coerceAtLeast(0))
        setQuantity.invoke(id, suggestion.expensive.card, 0, suggestion.expensive.section)
        if (addQuantity > 0) setQuantity.invoke(id, suggestion.alternative, addQuantity, suggestion.expensive.section)
        suggestBudget()
    }
    fun analyzeCombos() = viewModelScope.launch {
        val id = selected.value?.id ?: return@launch
        completeBusy = true
        val report = analyzeDeckCombos.invoke(id, format.value)
        val lines = report.lines.map { toComboPreview(it) }
        val actions = report.actions
            .distinctBy { it.cardId }
            .map { action ->
                relatedRef(
                    action.cardId,
                    when (action.kind) {
                        ComboActionKind.ADD_RECOVERY -> "combo_recovery"
                        ComboActionKind.BOOST_COPIES -> "combo_present"
                        ComboActionKind.ADD_PARTNER -> "combo_missing"
                    },
                )
            }
        comboReport = DeckComboReportUi(lines = lines, actionCards = actions)
        completeBusy = false
    }
    fun generateFlows() = viewModelScope.launch {
        val id = selected.value?.id ?: return@launch
        completeBusy = true
        when (val result = generateDeckFlows.invoke(id, format.value)) {
            is AppResult.Ok -> {
                val n = result.value.linkedFlowIds.size
                completeNotice = if (n == 0) "flows:0" else "flows:$n"
            }
            is AppResult.Err -> completeNotice = "err:${result.error.errorKey}"
        }
        completeBusy = false
    }
    fun completeDeck(
        targetMain: Int,
        targetExtra: Int,
        targetSide: Int,
        extraStaples: Set<String> = FormatExtraStaples.defaultNames,
    ) = viewModelScope.launch {
        val id = selected.value?.id ?: return@launch
        completeBusy = true
        when (val result = completeDeckUseCase.invoke(
            id,
            targetMain,
            targetExtra,
            targetSide,
            format.value,
            extraStaples,
        )) {
            is AppResult.Ok -> {
                val plan = result.value
                val mainAdded = plan.addedMain.sumOf { it.second }
                val extraAdded = plan.addedExtra.sumOf { it.second }
                val sideAdded = plan.addedSide.sumOf { it.second }
                completeNotice = if (mainAdded == 0 && extraAdded == 0 && sideAdded == 0) {
                    "empty"
                } else {
                    "ok:$mainAdded:$extraAdded:$sideAdded:${plan.mainTotal}:${plan.extraTotal}:${plan.sideTotal}"
                }
            }
            is AppResult.Err -> completeNotice = "err:${result.error.errorKey}"
        }
        completeBusy = false
    }
    fun rename(name: String) = viewModelScope.launch {
        val id = selected.value?.id ?: return@launch
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) renameDeck.invoke(id, trimmed)
    }
    fun change(card: DeckCard, delta: Int) = viewModelScope.launch {
        val maximum = evaluateLegality.maxCopies(card.card.id, format.value)
        val next = (card.quantity + delta).coerceIn(0, maximum)
        selected.value?.id?.let { setQuantity.invoke(it, card.card, next, card.section) }
        detail?.takeIf { it.card.id == card.card.id }?.let {
            detail = it.copy(inDeckQuantity = next, maxCopies = maximum)
        }
    }
    fun maxCopies(cardId: Int) = evaluateLegality.maxCopies(cardId, format.value)
    fun openDetail(card: DeckCard) = viewModelScope.launch {
        val fmt = format.value
        val localized = getLocalized.invoke(card.card.id, language.value) ?: card.card
        val script = getScript.invoke(card.card.id)
        val combos = suggestCombos.invoke(card.card.id, selected.value?.id, fmt)
        detail = CardDetailState(
            card = localized,
            maxCopies = evaluateLegality.maxCopies(card.card.id, fmt),
            inDeckQuantity = card.quantity,
            roles = script?.roles.orEmpty(),
            effectTags = script?.tags.orEmpty(),
            related = getRelated.invoke(card.card.id, fmt, 36),
            formatLabel = fmt.id.uppercase(),
            comboLines = combos.map { toComboPreview(it) },
        )
    }
    fun closeDetail() { detail = null }
    fun saveToCollection(collectionId: Long, cardId: Int) = viewModelScope.launch {
        profile.addCardToCollection(collectionId, cardId)
    }
    fun createCollectionAndSave(name: String, cardId: Int) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val id = profile.createCollection(trimmed)
        profile.addCardToCollection(id, cardId)
    }
    fun openCard(card: Card) = viewModelScope.launch {
        val localized = getLocalized.invoke(card.id, language.value) ?: card
        val section = if (isExtraDeckType(localized.type)) DeckSection.EXTRA else DeckSection.MAIN
        val inDeck = selected.value?.cards
            ?.filter { it.card.id == localized.id && it.section == section }
            ?.sumOf { it.quantity }
            ?: 0
        openDetail(DeckCard(localized, inDeck, section))
    }
    fun openRelated(ref: RelatedCardRef) = viewModelScope.launch {
        val localized = getLocalized.invoke(ref.id, language.value)
            ?: Card(id = ref.id, name = ref.name, type = "", imageUrl = ref.imageUrl)
        val section = if (isExtraDeckType(localized.type)) DeckSection.EXTRA else DeckSection.MAIN
        val inDeck = selected.value?.cards
            ?.filter { it.card.id == localized.id && it.section == section }
            ?.sumOf { it.quantity }
            ?: 0
        openDetail(DeckCard(localized, inDeck, section))
    }
    fun addRelated(ref: RelatedCardRef) = viewModelScope.launch {
        val deckId = selected.value?.id ?: return@launch
        val card = getLocalized.invoke(ref.id, language.value)
            ?: Card(id = ref.id, name = ref.name, type = "", imageUrl = ref.imageUrl)
        val section = if (isExtraDeckType(card.type)) DeckSection.EXTRA else DeckSection.MAIN
        val current = selected.value?.cards
            ?.filter { it.card.id == card.id && it.section == section }
            ?.sumOf { it.quantity }
            ?: 0
        val max = evaluateLegality.maxCopies(card.id, format.value)
        if (max > 0 && current < max) {
            setQuantity.invoke(deckId, card, current + 1, section)
        }
    }
    private suspend fun refreshOpenDetail(fmt: GameFormat, lang: AppLanguage) {
        val state = detail ?: return
        val localized = getLocalized.invoke(state.card.id, lang) ?: state.card
        val script = getScript.invoke(state.card.id)
        val combos = suggestCombos.invoke(state.card.id, selected.value?.id, fmt)
        detail = state.copy(
            card = localized,
            maxCopies = evaluateLegality.maxCopies(state.card.id, fmt),
            roles = script?.roles.orEmpty(),
            effectTags = script?.tags.orEmpty(),
            related = getRelated.invoke(state.card.id, fmt, 36),
            formatLabel = fmt.id.uppercase(),
            comboLines = combos.map { toComboPreview(it) },
        )
    }

    private suspend fun toComboPreview(line: ComboLineAdvice): ComboLinePreview {
        val keyCards = line.keyCardIds.map { id ->
            val relation = when {
                id in line.missingKeyCardIds -> "combo_missing"
                else -> "combo_present"
            }
            relatedRef(id, relation)
        } + line.actions
            .filter { it.kind == ComboActionKind.ADD_RECOVERY }
            .map { relatedRef(it.cardId, "combo_recovery") }
            .distinctBy { it.id }
            .filter { ref -> line.keyCardIds.none { it == ref.id } }
        val tips = buildList {
            for (choke in line.chokes) {
                add("Choke (step ${choke.stepIndex + 1}): ${choke.reason}")
            }
            for (action in line.actions.take(5)) {
                val prefix = when (action.kind) {
                    ComboActionKind.BOOST_COPIES -> "Raise to ${action.suggestedCopies}×"
                    else -> "Add ${action.suggestedCopies}×"
                }
                add("$prefix — ${action.reason}")
            }
        }
        return ComboLinePreview(
            flowId = line.flowId,
            title = line.title,
            tags = line.tags,
            presentCount = line.presentKeyCount,
            keyCount = line.keyCount,
            keyCards = keyCards,
            tips = tips,
        )
    }

    private suspend fun relatedRef(cardId: Int, relation: String): RelatedCardRef {
        val card = getLocalized.invoke(cardId, language.value)
        return RelatedCardRef(
            id = cardId,
            name = card?.name ?: "Card $cardId",
            relation = relation,
            score = 3.0,
        )
    }

    fun syncDetailQuantity(deck: Decklist?) {
        val current = detail ?: return
        val qty = deck?.cards?.firstOrNull { it.card.id == current.card.id }?.quantity ?: 0
        if (current.inDeckQuantity != qty) {
            detail = current.copy(inDeckQuantity = qty)
        }
    }
    fun toggleCover(cardId: Int) = viewModelScope.launch {
        val deck = selected.value ?: return@launch
        val current = deck.coverCardIds.toMutableList()
        if (cardId in current) current.remove(cardId) else {
            if (current.size >= 2) current.removeAt(0)
            current += cardId
        }
        setCovers.invoke(deck.id, current)
    }
    fun openImport() { _io.value = DeckIoUiState(importOpen = true) }
    fun openExport() { if (selected.value != null) _io.value = DeckIoUiState(exportOpen = true) }
    fun closeIo() { _io.value = DeckIoUiState() }
    fun consumeNotice() { _io.update { it.copy(noticeKey = null) } }
    fun copied() { _io.update { it.copy(noticeKey = "feedback.copied") } }
    fun export(format: DeckIoFormat): String = when (format) {
        DeckIoFormat.TEXT -> exportText.invoke(selected.value?.cards.orEmpty())
        DeckIoFormat.YDKE -> exportYdke.invoke(selected.value?.cards.orEmpty())
        DeckIoFormat.YDK -> exportYdk.invoke(selected.value?.cards.orEmpty())
    }
    fun import(
        name: String,
        content: String,
        format: DeckIoFormat,
        isExternal: Boolean = false,
        groupName: String? = null,
    ) = viewModelScope.launch {
        _io.update { it.copy(busy = true, error = null) }
        val decoded = when (format) {
            DeckIoFormat.TEXT -> importText.invoke(content)
            DeckIoFormat.YDKE -> importYdke.invoke(content)
            DeckIoFormat.YDK -> importYdk.invoke(content)
        }
        val persisted = when (decoded) {
            is AppResult.Err -> decoded
            is AppResult.Ok -> persistImported.invoke(name, decoded.value, isExternal, groupName)
        }
        when (persisted) {
            is AppResult.Err -> _io.update { it.copy(busy = false, error = persisted.error) }
            is AppResult.Ok -> {
                selectedId.value = persisted.value
                _io.value = DeckIoUiState(noticeKey = "deck.imported")
            }
        }
    }
}

@Composable fun DecksRoute(vm: DecklistViewModel = hiltViewModel()) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val io by vm.io.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val snackbarCopied = stringResource(DesignR.string.feedback_copied)
    val completeNothing = stringResource(DesignR.string.editor_complete_nothing)
    var showForgeSplash by remember { mutableStateOf(false) }
    LaunchedEffect(io.noticeKey) {
        when (io.noticeKey) {
            "feedback.copied" -> {
                snackbar.showSnackbar(snackbarCopied)
                vm.consumeNotice()
            }
            "deck.imported" -> {
                showForgeSplash = true
                vm.consumeNotice()
            }
            null -> Unit
            else -> vm.consumeNotice()
        }
    }
    LaunchedEffect(selected, vm.detail?.card?.id) {
        vm.syncDetailQuantity(selected)
    }
    val context = LocalContext.current
    LaunchedEffect(vm.completeNotice) {
        val raw = vm.completeNotice ?: return@LaunchedEffect
        val message = when {
            raw == "empty" -> completeNothing
            raw.startsWith("ok:") -> {
                val parts = raw.removePrefix("ok:").split(":")
                context.getString(
                    DesignR.string.editor_complete_done,
                    parts.getOrNull(0)?.toIntOrNull() ?: 0,
                    parts.getOrNull(1)?.toIntOrNull() ?: 0,
                    parts.getOrNull(2)?.toIntOrNull() ?: 0,
                    parts.getOrNull(3)?.toIntOrNull() ?: 0,
                    parts.getOrNull(4)?.toIntOrNull() ?: 0,
                    parts.getOrNull(5)?.toIntOrNull() ?: 0,
                )
            }
            raw.startsWith("flows:") -> {
                val n = raw.removePrefix("flows:").toIntOrNull() ?: 0
                if (n == 0) {
                    context.getString(DesignR.string.editor_generate_flows_none)
                } else {
                    context.getString(DesignR.string.editor_generate_flows_done, n)
                }
            }
            raw.startsWith("err:") -> context.getString(
                com.ygochecker.core.designsystem.errorStringResource(raw.removePrefix("err:")),
            )
            else -> raw
        }
        snackbar.showSnackbar(message)
        vm.clearCompleteNotice()
    }
    Box(Modifier.fillMaxSize()) {
        if (selected == null) DeckList(decks, vm::select, vm::create, vm::openImport)
        else DeckEditor(
            deck = selected!!,
            format = format,
            maxCopies = vm::maxCopies,
            back = vm::select,
            delete = vm::delete,
            change = vm::change,
            export = vm::openExport,
            onOpenCard = vm::openDetail,
            onToggleCover = vm::toggleCover,
            onRename = vm::rename,
            onSetPublic = { public -> selected?.let { vm.setVisibility(it.id, public) } },
            onCompleteDeck = vm::completeDeck,
            onGenerateFlows = vm::generateFlows,
            onAnalyzeCombos = vm::analyzeCombos,
            onSuggestBudget = vm::suggestBudget,
            completeBusy = vm.completeBusy,
        )
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (vm.completeBusy) 56.dp else 0.dp),
        )
        if (showForgeSplash) {
            DuelDeckForgeSplash(
                onFinished = { showForgeSplash = false },
                brand = stringResource(DesignR.string.import_forge_title),
                tagline = stringResource(DesignR.string.import_forge_tagline),
                holdMs = 1100L,
            )
        }
        if (vm.completeBusy) {
            DuelWorkingBanner(
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    if (io.importOpen) DeckImportSheet(io.busy, io.error, vm::closeIo, vm::import)
    if (io.exportOpen) DeckExportSheet(vm::export, vm::copied, vm::closeIo)
    vm.comboReport?.let { report ->
        DeckComboReportSheet(
            report = report,
            onDismiss = vm::closeComboReport,
            onOpen = vm::openRelated,
            onAdd = vm::addRelated,
        )
    }
    vm.budgetSuggestions?.let { suggestions ->
        BudgetSwapSheet(
            suggestions = suggestions,
            busy = vm.budgetBusy,
            onApply = vm::applyBudgetSwap,
            onOpenCard = vm::openCard,
            onDismiss = vm::closeBudgetSuggestions,
        )
    }
    var saveCollectionOpen by remember { mutableStateOf(false) }
    val collections by vm.collections.collectAsStateWithLifecycle()
    vm.detail?.let { state ->
        val card = selected?.cards?.firstOrNull { it.card.id == state.card.id }
        CardDetailSheet(
            state = state,
            onDismiss = vm::closeDetail,
            onDecrease = { card?.let { vm.change(it, -1) } },
            onIncrease = { card?.let { vm.change(it, 1) } },
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

@Composable private fun DeckList(
    decks: List<Decklist>,
    select: (Long?) -> Unit,
    create: () -> Unit,
    import: () -> Unit,
) {
    val newDeck = stringResource(DesignR.string.decks_new)
    var query by rememberSaveable { mutableStateOf("") }
    var sortNewestFirst by rememberSaveable { mutableStateOf(true) }
    val myDecks = remember(decks) { decks.filterNot(Decklist::isExternal) }
    val externalDecks = remember(decks) { decks.filter(Decklist::isExternal) }
    val visibleDecks = remember(myDecks, query, sortNewestFirst) {
        val filtered = if (query.isBlank()) myDecks else myDecks.filter { it.name.contains(query, ignoreCase = true) }
        if (sortNewestFirst) filtered.sortedByDescending(Decklist::updatedAt) else filtered.sortedBy { it.name.lowercase() }
    }
    val externalGroups = remember(externalDecks) {
        externalDecks.groupBy { it.groupName?.trim().takeUnless(String?::isNullOrBlank) }
            .toSortedMap(compareBy { it ?: "￿" })
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = create,
                icon = { Icon(Icons.Default.Add, newDeck) },
                text = { Text(newDeck) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ThemedScreenHeader(
                stringResource(DesignR.string.decks_title),
                stringResource(DesignR.string.decks_subtitle),
            )
            TextButton(
                onClick = import,
                modifier = Modifier.padding(horizontal = DuelSpacing.space3),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(DesignR.string.decks_import_action))
            }
            if (myDecks.isEmpty() && externalGroups.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Style,
                    title = stringResource(DesignR.string.decks_empty_title),
                    body = stringResource(DesignR.string.decks_empty_body),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text(stringResource(DesignR.string.decks_search_hint)) },
                    )
                    val sortDescription = stringResource(DesignR.string.decks_sort_toggle)
                    IconButton(onClick = { sortNewestFirst = !sortNewestFirst }) {
                        Icon(
                            if (sortNewestFirst) Icons.Default.Schedule else Icons.Default.SortByAlpha,
                            contentDescription = sortDescription,
                        )
                    }
                }
                if (visibleDecks.isEmpty() && externalGroups.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = stringResource(DesignR.string.decks_search_empty_title),
                        body = stringResource(DesignR.string.decks_search_empty_body),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = DuelSpacing.space4,
                            vertical = DuelSpacing.space2,
                        ),
                        verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
                    ) {
                        items(visibleDecks, key = Decklist::id, contentType = { "deck" }) { deck ->
                            DeckListRow(deck) { select(deck.id) }
                        }
                        if (externalGroups.isNotEmpty()) {
                            item(key = "external-header", contentType = "header") {
                                Text(
                                    stringResource(DesignR.string.decks_external_section),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = DuelSpacing.space3),
                                )
                            }
                            externalGroups.forEach { (groupName, groupDecks) ->
                                item(key = "group-${groupName ?: "none"}", contentType = "header") {
                                    Text(
                                        groupName ?: stringResource(DesignR.string.decks_ungrouped),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                items(groupDecks, key = { "ext-${it.id}" }, contentType = { "deck" }) { deck ->
                                    DeckListRow(deck) { select(deck.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckListRow(deck: Decklist, onOpen: () -> Unit) {
    val main = deck.cards.filter { it.section == DeckSection.MAIN }.sumOf(DeckCard::quantity)
    val extra = deck.cards.filter { it.section == DeckSection.EXTRA }.sumOf(DeckCard::quantity)
    val side = deck.cards.filter { it.section == DeckSection.SIDE }.sumOf(DeckCard::quantity)
    val covers = deckPresentationCards(deck, 2)
    val context = LocalContext.current
    // Soft vertical padding so cover arts can poke past the card chrome.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable(onClick = onOpen),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(76.dp),
        ) {}
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = DuelSpacing.space3, end = DuelSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            Box(
                Modifier
                    .width(86.dp)
                    .height(96.dp),
            ) {
                if (covers.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(56.dp, 76.dp)
                            .align(Alignment.Center),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Style, contentDescription = null)
                        }
                    }
                } else {
                    covers.forEachIndexed { index, item ->
                        val tilt = if (index == 0) -9f else 11f
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.card.imageUrl)
                                .size(Size(140, 200))
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(58.dp, 84.dp)
                                .align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd)
                                .offset(
                                    x = if (index == 0) (-4).dp else 6.dp,
                                    y = if (index == 0) (-4).dp else 2.dp,
                                )
                                .graphicsLayer {
                                    rotationZ = tilt
                                    shadowElevation = 10f
                                }
                                .clip(RoundedCornerShape(7.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DeckMetaRow(
                    listOf(stringResource(DesignR.string.decks_section_counts, main, extra, side)),
                )
                Text(
                    stringResource(DesignR.string.decks_tap_to_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun DeckEditor(
    deck: Decklist,
    format: GameFormat,
    maxCopies: (Int) -> Int,
    back: (Long?) -> Unit,
    delete: (Long) -> Unit,
    change: (DeckCard, Int) -> Unit,
    export: () -> Unit,
    onOpenCard: (DeckCard) -> Unit,
    onToggleCover: (Int) -> Unit,
    onRename: (String) -> Unit,
    onSetPublic: (Boolean) -> Unit,
    onCompleteDeck: (Int, Int, Int, Set<String>) -> Unit,
    onGenerateFlows: () -> Unit,
    onAnalyzeCombos: () -> Unit,
    onSuggestBudget: () -> Unit,
    completeBusy: Boolean,
) {
    var section by remember { mutableStateOf(DeckSection.MAIN) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var tournamentOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    var completeOpen by remember { mutableStateOf(false) }
    var targetMain by remember { mutableStateOf("40") }
    var targetExtra by remember { mutableStateOf("15") }
    var targetSide by remember { mutableStateOf("15") }
    var selectedStaples by remember(format) {
        mutableStateOf(FormatExtraStaples.defaultsFor(format))
    }
    var renameDraft by remember(deck.name) { mutableStateOf(deck.name) }
    val backDescription = stringResource(DesignR.string.editor_back)
    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(DesignR.string.editor_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton({
                    onRename(renameDraft)
                    renameOpen = false
                }) { Text(stringResource(DesignR.string.editor_rename_save)) }
            },
            dismissButton = {
                TextButton({ renameOpen = false }) {
                    Text(stringResource(DesignR.string.action_cancel))
                }
            },
        )
    }
    if (deleteConfirmOpen) {
        AlertDialog(
            onDismissRequest = { deleteConfirmOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            },
            title = { Text(stringResource(DesignR.string.decks_delete_confirm, deck.name)) },
            text = {
                Text(
                    stringResource(DesignR.string.decks_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmOpen = false
                        delete(deck.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(DesignR.string.decks_delete)) }
            },
            dismissButton = {
                TextButton({ deleteConfirmOpen = false }) {
                    Text(stringResource(DesignR.string.action_cancel))
                }
            },
        )
    }
    if (tournamentOpen) {
        TournamentCompanionDialog(deck = deck, onDismiss = { tournamentOpen = false })
    }
    if (completeOpen) {
        AlertDialog(
            onDismissRequest = { if (!completeBusy) completeOpen = false },
            title = { Text(stringResource(DesignR.string.editor_complete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(DesignR.string.editor_complete_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = targetMain,
                        onValueChange = { targetMain = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(DesignR.string.editor_complete_main_target)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = targetExtra,
                        onValueChange = { targetExtra = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(DesignR.string.editor_complete_extra_target)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = targetSide,
                        onValueChange = { targetSide = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(DesignR.string.editor_complete_side_target)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(DesignR.string.editor_complete_staples),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(DesignR.string.editor_complete_staples_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton({
                            selectedStaples = FormatExtraStaples.defaultsFor(format)
                        }) { Text(stringResource(DesignR.string.editor_complete_staples_all)) }
                        TextButton({
                            selectedStaples = emptySet()
                        }) { Text(stringResource(DesignR.string.editor_complete_staples_none)) }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FormatExtraStaples.entriesFor(format).forEach { entry ->
                            val on = entry.name in selectedStaples
                            FilterChip(
                                selected = on,
                                onClick = {
                                    selectedStaples = if (on) {
                                        selectedStaples - entry.name
                                    } else {
                                        selectedStaples + entry.name
                                    }
                                },
                                label = { Text(entry.shortLabel) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        onCompleteDeck(
                            targetMain.toIntOrNull() ?: 40,
                            targetExtra.toIntOrNull() ?: 15,
                            targetSide.toIntOrNull() ?: 15,
                            selectedStaples,
                        )
                        completeOpen = false
                    },
                    enabled = !completeBusy,
                ) { Text(stringResource(DesignR.string.editor_complete_run)) }
            },
            dismissButton = {
                TextButton({ completeOpen = false }, enabled = !completeBusy) {
                    Text(stringResource(DesignR.string.action_cancel))
                }
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        deck.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            renameDraft = deck.name
                            renameOpen = true
                        },
                    )
                },
                navigationIcon = {
                    IconButton({ back(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, backDescription)
                    }
                },
                actions = {
                    IconButton({ menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(DesignR.string.editor_actions))
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_rename)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuOpen = false
                                renameDraft = deck.name
                                renameOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_export)) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { menuOpen = false; export() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_complete_deck)) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                            onClick = { menuOpen = false; completeOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_generate_flows)) },
                            leadingIcon = { Icon(Icons.Default.AccountTree, null) },
                            onClick = { menuOpen = false; onGenerateFlows() },
                            enabled = !completeBusy,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_analyze_combos)) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                            onClick = { menuOpen = false; onAnalyzeCombos() },
                            enabled = !completeBusy,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_suggest_budget)) },
                            leadingIcon = { Icon(Icons.Default.Savings, null) },
                            onClick = { menuOpen = false; onSuggestBudget() },
                            enabled = !completeBusy,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.editor_tournament_companion)) },
                            leadingIcon = { Icon(Icons.Default.Bolt, null) },
                            onClick = { menuOpen = false; tournamentOpen = true },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (deck.isPublic) {
                                        stringResource(DesignR.string.decks_private)
                                    } else {
                                        stringResource(DesignR.string.decks_public)
                                    },
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (deck.isPublic) Icons.Default.Lock else Icons.Default.Public,
                                    null,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onSetPublic(!deck.isPublic)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(DesignR.string.decks_delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menuOpen = false
                                deleteConfirmOpen = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space2),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(DesignR.string.editor_format_banner, format.id.uppercase()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    DeckValueBadge(deck.cards)
                }
            }
            PrimaryTabRow(DeckSection.entries.indexOf(section)) {
                DeckSection.entries.forEach { item ->
                    val count = deck.cards.filter { it.section == item }.sumOf(DeckCard::quantity)
                    Tab(section == item, { section = item }, text = { Text("${sectionLabel(item)} · $count") })
                }
            }
            val cards = deck.cards.filter { it.section == section }
            if (cards.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Add,
                    title = stringResource(DesignR.string.editor_empty_section),
                    body = stringResource(DesignR.string.search_subtitle),
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(DuelSpacing.space4),
                    verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
                ) {
                    items(cards, key = { "${it.card.id}-${it.section}" }, contentType = { "deck-card" }) { item ->
                        EditorCardRow(
                            item = item,
                            format = format,
                            maximum = maxCopies(item.card.id),
                            coverSlot = deck.coverCardIds.indexOf(item.card.id).takeIf { it >= 0 }?.plus(1),
                            onChange = { delta -> change(item, delta) },
                            onOpen = { onOpenCard(item) },
                            onToggleCover = { onToggleCover(item.card.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorCardRow(
    item: DeckCard,
    format: GameFormat,
    maximum: Int,
    coverSlot: Int?,
    onChange: (Int) -> Unit,
    onOpen: () -> Unit,
    onToggleCover: () -> Unit,
) {
    val overLimit = item.quantity > maximum
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val imageRequest = remember(item.card.imageUrl) {
        ImageRequest.Builder(context)
            .data(item.card.imageUrl)
            .size(Size(88, 128))
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
        color = if (overLimit) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.padding(DuelSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AttributeBadge(item.card.attribute, compact = true)
                    Text(
                        item.card.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                StatusChip(
                    label = if (overLimit) {
                        stringResource(DesignR.string.legality_over_limit, format.id.uppercase(), maximum)
                    } else {
                        legalityLabel
                    },
                    tone = if (overLimit) StatusTone.Error else tone,
                )
                coverSlot?.let { slot ->
                    StatusChip(stringResource(DesignR.string.decks_cover_slot, slot), StatusTone.Info)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(DesignR.string.editor_actions))
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(DesignR.string.decks_set_cover)) },
                        onClick = { menuOpen = false; onToggleCover() },
                    )
                }
            }
            FilledTonalIconButton({ onChange(-1) }) {
                Icon(Icons.Default.Remove, stringResource(DesignR.string.editor_decrease))
            }
            Text(
                item.quantity.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            FilledIconButton({ onChange(1) }, enabled = item.quantity < maximum) {
                Icon(Icons.Default.Add, stringResource(DesignR.string.editor_increase))
            }
        }
    }
}

/** Lower-bound estimate: cards with no known price yet (never fetched from YGOPRODeck) are excluded, and a "+"
 * suffix flags that the true total is at least this high. Hidden entirely if no card has a price yet. */
@Composable
private fun DeckValueBadge(cards: List<DeckCard>) {
    if (cards.none { it.card.priceEur != null }) return
    val total = cards.sumOf { (it.card.priceEur ?: 0.0) * it.quantity }
    val suffix = if (cards.any { it.card.priceEur == null }) "+" else ""
    StatusChip(stringResource(DesignR.string.editor_deck_value, formatPriceEur(total) + suffix), StatusTone.Info)
}

@Composable
private fun sectionLabel(section: DeckSection): String = stringResource(
    when (section) {
        DeckSection.MAIN -> DesignR.string.editor_section_main
        DeckSection.EXTRA -> DesignR.string.editor_section_extra
        DeckSection.SIDE -> DesignR.string.editor_section_side
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckComboReportSheet(
    report: DeckComboReportUi,
    onDismiss: () -> Unit,
    onOpen: (RelatedCardRef) -> Unit,
    onAdd: (RelatedCardRef) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Text(
                stringResource(DesignR.string.editor_combo_report_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (report.lines.isEmpty()) {
                Text(
                    stringResource(DesignR.string.editor_combo_report_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (report.actionCards.isNotEmpty()) {
                    Text(
                        stringResource(DesignR.string.editor_combo_report_actions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    report.actionCards.forEach { ref ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(ref) },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                ref.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            FilledTonalIconButton(onClick = { onAdd(ref) }) {
                                Icon(Icons.Default.Add, stringResource(DesignR.string.detail_related_add))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Text(
                    stringResource(DesignR.string.editor_combo_report_lines),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                report.lines.forEach { line ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(DuelSpacing.space3),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(line.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    DesignR.string.detail_combo_coverage,
                                    line.presentCount,
                                    line.keyCount,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            line.tips.take(4).forEach { tip ->
                                Text(
                                    tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(DuelSpacing.space2))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSwapSheet(
    suggestions: List<BudgetSwapSuggestion>,
    busy: Boolean,
    onApply: (BudgetSwapSuggestion) -> Unit,
    onOpenCard: (Card) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Text(stringResource(DesignR.string.editor_budget_title), style = MaterialTheme.typography.titleLarge)
            when {
                busy -> Box(Modifier.fillMaxWidth().padding(vertical = DuelSpacing.space4), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                suggestions.isEmpty() -> Text(
                    stringResource(DesignR.string.editor_budget_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> suggestions.forEach { s ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(DuelSpacing.space3),
                            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${s.expensive.card.name} (${formatPriceEur(s.expensive.card.priceEur ?: 0.0)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onOpenCard(s.expensive.card) },
                                )
                                Text(
                                    "→ ${s.alternative.name} (${formatPriceEur(s.alternative.priceEur ?: 0.0)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onOpenCard(s.alternative) },
                                )
                                Text(
                                    stringResource(DesignR.string.editor_budget_savings, formatPriceEur(s.savingsEur)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FilledTonalButton(onClick = { onApply(s) }) {
                                Text(stringResource(DesignR.string.editor_budget_apply))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(DuelSpacing.space2))
        }
    }
}
