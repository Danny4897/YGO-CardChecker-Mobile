package com.ygochecker.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ygochecker.core.model.isExtraDeckType

enum class CardTypeBadgeKind { MONSTER, SPELL, TRAP, EXTRA }

fun cardTypeBadgeKindOf(type: String): CardTypeBadgeKind = when {
    isExtraDeckType(type) -> CardTypeBadgeKind.EXTRA
    type.contains("Spell", ignoreCase = true) -> CardTypeBadgeKind.SPELL
    type.contains("Trap", ignoreCase = true) -> CardTypeBadgeKind.TRAP
    else -> CardTypeBadgeKind.MONSTER
}

private fun CardTypeBadgeKind.frameColor(): Color = when (this) {
    CardTypeBadgeKind.MONSTER -> Color(0xFFE8C45C)
    CardTypeBadgeKind.SPELL -> Color(0xFF3DDC97)
    CardTypeBadgeKind.TRAP -> Color(0xFFE85AA0)
    CardTypeBadgeKind.EXTRA -> Color(0xFFB14EFF)
}

/** Mini card-silhouette badge: rounded frame in the type color, with an inner
 * art-box band when not [compact]. [compact] renders just the tinted frame,
 * sized for a 16dp filter-chip leading icon slot. */
@Composable
fun CardTypeBadge(type: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val kind = remember(type) { cardTypeBadgeKindOf(type) }
    val color = kind.frameColor()
    val height = if (compact) 18.dp else 40.dp
    val width = height * 0.72f
    Box(
        modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .border(if (compact) 1.dp else 1.5.dp, color, RoundedCornerShape(4.dp)),
    ) {
        if (!compact) {
            Box(
                Modifier
                    .padding(3.dp)
                    .fillMaxWidth()
                    .height(height * 0.42f)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.32f)),
            )
        }
    }
}
