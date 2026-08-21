package com.ygochecker.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AttributeSpec(val kanji: String, val sphereColor: Color, val sphereColorDark: Color)

private val ATTRIBUTE_SPECS = mapOf(
    "DARK" to AttributeSpec("闇", Color(0xFFC98FE8), Color(0xFF2A1140)),
    "LIGHT" to AttributeSpec("光", Color(0xFFF0D489), Color(0xFF4A3810)),
    "WATER" to AttributeSpec("水", Color(0xFF7FD4F5), Color(0xFF0D3560)),
    "FIRE" to AttributeSpec("炎", Color(0xFFFF8A6A), Color(0xFF5C0F13)),
    "EARTH" to AttributeSpec("地", Color(0xFFA68A5C), Color(0xFF241A0D)),
    "WIND" to AttributeSpec("風", Color(0xFF6FDB8F), Color(0xFF14401F)),
    "DIVINE" to AttributeSpec("神", Color(0xFFFFE9A8), Color(0xFF6B4E10)),
)

fun attributeSpecOrNull(attribute: String?): AttributeSpec? =
    attribute?.uppercase()?.let { ATTRIBUTE_SPECS[it] }

/** Glossy elemental sphere + kanji glyph. Renders nothing when [attribute] doesn't
 * resolve (e.g. Spell/Trap cards have no attribute). [compact] drops the kanji
 * (illegible below ~30dp) and shows just the tinted sphere, for filter-chip use. */
@Composable
fun AttributeBadge(attribute: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val spec = attributeSpecOrNull(attribute) ?: return
    val size = if (compact) 18.dp else 46.dp
    val density = LocalDensity.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(spec.sphereColor, spec.sphereColorDark),
                    center = with(density) { Offset(size.toPx() * 0.32f, size.toPx() * 0.28f) },
                    radius = with(density) { size.toPx() * 0.9f },
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!compact) {
            Text(spec.kanji, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}
