package com.ygochecker.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ygochecker.core.designsystem.AlertBell
import com.ygochecker.core.designsystem.DuelInsets
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.duelExtendedColors
import com.ygochecker.core.designsystem.EmptyState
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.StatusChip
import com.ygochecker.core.designsystem.StatusTone
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.domain.EvaluateDeckLegality
import com.ygochecker.core.domain.FormatPreference
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.SocialRepository
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.DeckLegality
import com.ygochecker.core.model.GameFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    listDecks: ListDecklists,
    private val evaluateLegality: EvaluateDeckLegality,
    formatPreference: FormatPreference,
    private val social: SocialRepository,
) : ViewModel() {
    val decks = listDecks.invoke().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val format = formatPreference.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameFormat.TCG)
    val alerts = social.observeAlerts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun legalityFor(deck: Decklist, format: GameFormat): DeckLegality = evaluateLegality.invoke(deck, format)

    /** No dedicated alert feed yet (nothing populates `user_alerts` until a future pass) — tapping the bell just clears the badge. */
    fun markAllAlertsRead() = viewModelScope.launch {
        val unreadIds = alerts.value.filter { !it.read }.map { it.id }
        social.markAlertsRead(unreadIds)
    }
}

/**
 * Landing dashboard: unread alerts, the most recently edited deck's legality at a glance,
 * and shortcuts into Decks/Flow/Search/Scan. First of 5 primary tabs — Settings/Overlay are
 * reached from the gear icon in the top bar instead (see LocalOpenSettings).
 */
@Composable
fun HomeRoute(
    onOpenSearch: () -> Unit,
    onOpenDecks: () -> Unit,
    onOpenFlow: () -> Unit,
    onOpenScan: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val decks by vm.decks.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val unreadCount = remember(alerts) { alerts.count { !it.read } }
    val latestDeck = remember(decks) { decks.maxByOrNull { it.updatedAt } }
    val legality = remember(latestDeck, format) { latestDeck?.let { vm.legalityFor(it, format) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ThemedScreenHeader(
            title = stringResource(DesignR.string.home_title),
            subtitle = stringResource(DesignR.string.home_subtitle),
            actions = { AlertBell(unreadCount = unreadCount, onClick = vm::markAllAlertsRead) },
        )
        Column(
            Modifier.padding(DuelInsets.screen),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space4),
        ) {
            if (latestDeck != null) {
                LastDeckCard(deck = latestDeck, legality = legality, onClick = onOpenDecks)
            } else {
                EmptyState(
                    icon = Icons.Default.Style,
                    title = stringResource(DesignR.string.home_no_decks_title),
                    body = stringResource(DesignR.string.home_no_decks_body),
                    primaryLabel = stringResource(DesignR.string.home_open_decks),
                    onPrimary = onOpenDecks,
                )
            }
            ShortcutRow(
                icon = Icons.Default.Style,
                title = stringResource(DesignR.string.nav_decks),
                subtitle = stringResource(DesignR.string.home_last_deck),
                onClick = onOpenDecks,
            )
            ShortcutRow(
                icon = Icons.Default.AccountTree,
                title = stringResource(DesignR.string.home_flow_title),
                subtitle = stringResource(DesignR.string.home_flow_subtitle),
                onClick = onOpenFlow,
            )
            ShortcutRow(
                icon = Icons.Default.Search,
                title = stringResource(DesignR.string.home_search_title),
                subtitle = stringResource(DesignR.string.home_search_subtitle),
                onClick = onOpenSearch,
            )
            ShortcutRow(
                icon = Icons.Default.CameraAlt,
                title = stringResource(DesignR.string.home_scan_title),
                subtitle = stringResource(DesignR.string.home_scan_subtitle),
                onClick = onOpenScan,
            )
        }
    }
}

@Composable
private fun LastDeckCard(deck: Decklist, legality: DeckLegality?, onClick: () -> Unit) {
    val accentColor = when (legality?.isLegal) {
        true -> MaterialTheme.duelExtendedColors.success
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )
            Column(Modifier.padding(DuelSpacing.space4)) {
                Text(
                    stringResource(DesignR.string.home_last_deck),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(deck.name, style = MaterialTheme.typography.titleMedium)
                if (legality != null) {
                    StatusChip(
                        label = if (legality.isLegal) {
                            stringResource(DesignR.string.home_deck_legal)
                        } else {
                            stringResource(DesignR.string.home_deck_illegal)
                        },
                        tone = if (legality.isLegal) StatusTone.Success else StatusTone.Error,
                        modifier = Modifier.padding(top = DuelSpacing.space2),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(DuelSpacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(DuelSpacing.space2))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
