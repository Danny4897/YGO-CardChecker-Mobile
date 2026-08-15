package com.ygochecker.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Opens the app navigation drawer (Profile / Settings). Provided by AppShell. */
val LocalOpenDrawer = compositionLocalOf<(() -> Unit)?> { null }

enum class StatusTone { Neutral, Success, Warning, Error, Info }

/**
 * Soft HAT-themed screen header (variant C): atmospheric foil band, gold title accent,
 * gentle fade into content — not a busy collage.
 * [statusBarsPadding] + min height so Search/Decks (Scaffold) match Overlay/Flow/Settings.
 */
@Composable
fun ThemedScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val bandTop = scheme.surfaceContainerLowest
    val bandMid = scheme.surfaceContainer
    val fadeTo = scheme.background

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(bandTop, bandMid, fadeTo),
                ),
            ),
    ) {
        Image(
            painter = painterResource(R.drawable.header_foil_texture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.28f,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)),
        )
        Image(
            painter = painterResource(R.drawable.header_card_pattern),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            alpha = 0.55f,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.TopCenter),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, fadeTo),
                    ),
                ),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 112.dp)
                .padding(
                    start = DuelSpacing.space4,
                    end = DuelSpacing.space4,
                    top = DuelSpacing.space3,
                    bottom = DuelSpacing.space4,
                ),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
            ) {
                val openDrawer = LocalOpenDrawer.current
                if (openDrawer != null) {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_menu))
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = actions,
                )
            }
            Box(
                Modifier
                    .padding(top = DuelSpacing.space2)
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                scheme.primary,
                                scheme.primary.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
            )
            Text(
                subtitle.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = DuelSpacing.space2)
                    .heightIn(min = 40.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    ThemedScreenHeader(title = title, subtitle = subtitle)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DuelSpacing.space8, vertical = DuelSpacing.space6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(DuelSpacing.space5))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(DuelSpacing.space2))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(DuelSpacing.space5))
            androidx.compose.material3.Button(onClick = onPrimary) { Text(primaryLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(DuelSpacing.space1))
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

@Composable
fun StatusChip(label: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.duelExtendedColors
    val (container, content) = when (tone) {
        StatusTone.Success -> colors.successContainer to colors.onSuccessContainer
        StatusTone.Warning -> colors.warningContainer to colors.onWarningContainer
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.Info -> colors.infoContainer to colors.onInfoContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
                    Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = DuelSpacing.space4)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = DuelSpacing.space2, start = DuelSpacing.space1),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(DuelSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                content = content,
            )
        }
    }
}

@Composable
fun DeckMetaRow(items: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { label ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Localized effect-mechanic chip label (Title Case via string resources). */
@Composable
fun effectMechanicLabel(tag: String): String {
    val res = when (tag) {
        "gy_effect" -> R.string.search_effect_gy_effect
        "ss_from_gy" -> R.string.search_effect_ss_from_gy
        "ss_from_hand" -> R.string.search_effect_ss_from_hand
        "ss_from_deck" -> R.string.search_effect_ss_from_deck
        "ss_from_extra" -> R.string.search_effect_ss_from_extra
        "special_summons" -> R.string.search_effect_special_summons
        "searches_deck" -> R.string.search_effect_searches_deck
        "draw" -> R.string.search_effect_draw
        "destroys" -> R.string.search_effect_destroys
        "banishes" -> R.string.search_effect_banishes
        "negates" -> R.string.search_effect_negates
        "discards" -> R.string.search_effect_discards
        "sends_to_gy" -> R.string.search_effect_sends_to_gy
        "hand_to_gy" -> R.string.search_effect_hand_to_gy
        "revives_from_gy" -> R.string.search_effect_revives_from_gy
        "mills" -> R.string.search_effect_mills
        "quick_effect" -> R.string.search_effect_quick_effect
        "trigger_effect" -> R.string.search_effect_trigger_effect
        "changes_level" -> R.string.search_effect_changes_level
        else -> null
    }
    return if (res != null) {
        stringResource(res)
    } else {
        com.ygochecker.core.model.EffectMechanicTags.displayLabel(tag)
    }
}

object DuelInsets {
    val screen = PaddingValues(horizontal = DuelSpacing.space4, vertical = DuelSpacing.space3)
}
