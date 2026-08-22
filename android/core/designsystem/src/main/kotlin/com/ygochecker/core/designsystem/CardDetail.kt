package com.ygochecker.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.RelatedCardRef

data class ComboLinePreview(
    val flowId: String,
    val title: String,
    val tags: List<String> = emptyList(),
    val presentCount: Int,
    val keyCount: Int,
    val keyCards: List<RelatedCardRef> = emptyList(),
    /** Choke + consistency tips (authored / engine text). */
    val tips: List<String> = emptyList(),
)

data class CardDetailState(
    val card: Card,
    val maxCopies: Int,
    val inDeckQuantity: Int = 0,
    val roles: List<String> = emptyList(),
    val effectTags: List<String> = emptyList(),
    val related: List<RelatedCardRef> = emptyList(),
    /** Format id → max copies for banlist compare row. */
    val banlistByFormat: List<Pair<String, Int>> = emptyList(),
    val formatLabel: String = "",
    /** Deck-building combo lines for this card (HAT Flow assist). */
    val comboLines: List<ComboLinePreview> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailSheet(
    state: CardDetailState,
    onDismiss: () -> Unit,
    onDecrease: (() -> Unit)? = null,
    onIncrease: (() -> Unit)? = null,
    onRelatedOpen: ((RelatedCardRef) -> Unit)? = null,
    onRelatedAdd: ((RelatedCardRef) -> Unit)? = null,
    onSaveToCollection: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CardDetailContent(
            state = state,
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            onRelatedOpen = onRelatedOpen,
            onRelatedAdd = onRelatedAdd,
            onSaveToCollection = onSaveToCollection,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = DuelSpacing.space4)
                .padding(bottom = DuelSpacing.space4),
        )
    }
}

@Composable
fun CardDetailContent(
    state: CardDetailState,
    modifier: Modifier = Modifier,
    onDecrease: (() -> Unit)? = null,
    onIncrease: (() -> Unit)? = null,
    onRelatedOpen: ((RelatedCardRef) -> Unit)? = null,
    onRelatedAdd: ((RelatedCardRef) -> Unit)? = null,
    onSaveToCollection: (() -> Unit)? = null,
) {
    val card = state.card
    val context = LocalContext.current
    val largeArt = "https://images.ygoprodeck.com/images/cards/${card.id}.jpg"
    val (legalityLabel, tone) = when (state.maxCopies) {
        0 -> stringResource(R.string.legality_forbidden) to StatusTone.Error
        1 -> stringResource(R.string.legality_limited) to StatusTone.Warning
        2 -> stringResource(R.string.legality_semi_limited) to StatusTone.Warning
        else -> stringResource(R.string.legality_valid) to StatusTone.Success
    }
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(120.dp)
                    .height(176.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(largeArt)
                        .size(Size(240, 352))
                        .crossfade(false)
                        .build(),
                    contentDescription = card.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(card.type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(legalityLabel, tone)
                    card.priceEur?.let { StatusChip(formatPriceEur(it), StatusTone.Info) }
                }
                if (state.formatLabel.isNotBlank()) {
                    Text(
                        stringResource(R.string.editor_format_banner, state.formatLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDecrease != null && onIncrease != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = onDecrease, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Remove, stringResource(R.string.editor_decrease))
                        }
                        Text(
                            state.inDeckQuantity.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        FilledIconButton(
                            onClick = onIncrease,
                            enabled = state.inDeckQuantity < state.maxCopies,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Add, stringResource(R.string.editor_increase))
                        }
                    }
                }
                if (onSaveToCollection != null) {
                    FilledTonalButton(
                        onClick = onSaveToCollection,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.search_save_to_collection))
                    }
                }
            }
        }
        metaRow(card)
        HorizontalDivider()
        if (state.comboLines.isNotEmpty()) {
            Text(
                stringResource(R.string.detail_combo_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.detail_combo_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.comboLines.forEach { line ->
                ComboLineCard(
                    line = line,
                    onOpen = { ref -> onRelatedOpen?.invoke(ref) },
                    onAdd = onRelatedAdd,
                )
            }
            HorizontalDivider()
        }
        Text(
            stringResource(R.string.detail_effect),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            card.description.ifBlank { stringResource(R.string.detail_effect_empty) },
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()
        Text(
            stringResource(R.string.detail_related),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.formatLabel.isNotBlank()) {
            Text(
                stringResource(R.string.detail_related_playable_hint, state.formatLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.related.isEmpty()) {
            Text(
                stringResource(R.string.detail_related_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.related.forEach { ref ->
                    RelatedCardTile(
                        ref = ref,
                        onOpen = { onRelatedOpen?.invoke(ref) },
                        onAdd = onRelatedAdd?.let { add -> { add(ref) } },
                    )
                }
            }
        }
        if (state.roles.isNotEmpty()) {
            Text(
                stringResource(R.string.detail_roles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            CompactChipRow(state.roles.map { scriptRoleLabel(it) })
        }
        if (state.effectTags.isNotEmpty()) {
            Text(
                stringResource(R.string.detail_effect_tags),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            CompactChipRow(state.effectTags.map { effectMechanicLabel(it) })
        }
        Spacer(Modifier.height(DuelSpacing.space2))
    }
}

@Composable
private fun ComboLineCard(
    line: ComboLinePreview,
    onOpen: (RelatedCardRef) -> Unit,
    onAdd: ((RelatedCardRef) -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(DuelSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(line.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line.tags.isNotEmpty()) {
                CompactChipRow(line.tags)
            }
            Text(
                stringResource(R.string.detail_combo_coverage, line.presentCount, line.keyCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (line.keyCards.isNotEmpty()) {
                Text(
                    stringResource(R.string.detail_combo_key_cards),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    line.keyCards.forEach { ref ->
                        RelatedCardTile(
                            ref = ref,
                            onOpen = { onOpen(ref) },
                            onAdd = if (ref.relation == "combo_missing" || ref.relation == "combo_recovery") {
                                onAdd?.let { add -> { add(ref) } }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            line.tips.forEach { tip ->
                Text(
                    tip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactChipRow(labels: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun scriptRoleLabel(role: String): String {
    val res = when (role.trim().lowercase()) {
        "starter" -> R.string.role_starter
        "extender" -> R.string.role_extender
        "tech" -> R.string.role_tech
        "handtrap", "hand_trap" -> R.string.role_handtrap
        "engine" -> R.string.role_engine
        "board_breaker", "board-breaker" -> R.string.role_board_breaker
        else -> null
    }
    return if (res != null) stringResource(res) else role.replaceFirstChar(Char::titlecase)
}

@Composable
private fun relationLabel(relation: String): String {
    val key = relation.trim().lowercase()
    val res = when {
        key.contains("mill") -> R.string.relation_mill
        key.contains("banish") -> R.string.relation_banish
        key.contains("effect") -> R.string.relation_effect
        key.contains("type_peer") || key.contains("peer") -> R.string.relation_type_peer
        key.contains("engine") -> R.string.relation_engine
        key.contains("gy") -> R.string.relation_gy
        key.contains("archetype") || key.contains("series") -> R.string.relation_archetype
        key.contains("combo_missing") -> R.string.relation_combo_missing
        key.contains("combo_in") || key == "combo_present" -> R.string.relation_combo_present
        key.contains("combo_recovery") -> R.string.relation_combo_recovery
        key.contains("hat") -> R.string.relation_engine
        key.contains("manual") || key.contains("synergy") -> R.string.relation_synergy
        else -> null
    }
    return if (res != null) stringResource(res) else relation.replace('_', ' ').replaceFirstChar(Char::titlecase)
}

@Composable
private fun RelatedCardTile(
    ref: RelatedCardRef,
    onOpen: () -> Unit,
    onAdd: (() -> Unit)?,
) {
    val context = LocalContext.current
    val artUrl = ref.imageUrl
        .replace("/cards_small/", "/cards/")
        .replace("/cards_cropped/", "/cards/")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(59f / 86f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .size(Size(236, 344))
                        .crossfade(false)
                        .build(),
                    contentDescription = ref.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                ref.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 32.dp),
            )
            Text(
                relationLabel(ref.relation),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onAdd != null) {
                FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(32.dp).align(Alignment.End)) {
                    Icon(Icons.Default.Add, stringResource(R.string.detail_related_add))
                }
            }
        }
    }
}

@Composable
private fun metaRow(card: Card) {
    val bits = buildList {
        card.attribute?.let { add(it) }
        card.race?.let { add(it) }
        card.level?.let { add("Lv $it") }
        card.attack?.let { add("ATK $it") }
        card.defense?.let { add("DEF $it") }
        add("ID ${card.id}")
    }
    if (bits.isNotEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                bits.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(DuelSpacing.space3),
            )
        }
    }
}
