package com.ygochecker.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ygochecker.core.model.ProfileAvatarPresets
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original duel emblems for profile avatars — silhouettes only, no card artwork.
 */
@Composable
fun ProfileEmblem(
    style: ProfileAvatarPresets.Style,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val gold = MaterialTheme.colorScheme.primary
    val ink = Color(0xFF12141C)
    val accent = when (style) {
        ProfileAvatarPresets.Style.AZURE_DRAKE -> Color(0xFF6EC8FF)
        ProfileAvatarPresets.Style.ARCANE_ORB -> Color(0xFFB388FF)
        ProfileAvatarPresets.Style.STAR_DUELIST -> gold
        ProfileAvatarPresets.Style.ROSE_KNIGHT -> Color(0xFFE5739A)
        ProfileAvatarPresets.Style.VOID_WARRIOR -> Color(0xFF90A4AE)
        ProfileAvatarPresets.Style.GOLDEN_SHIELD -> gold
        ProfileAvatarPresets.Style.PHOENIX_CREST -> Color(0xFFFF8A65)
        ProfileAvatarPresets.Style.ICE_SERPENT -> Color(0xFF80DEEA)
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(ink.copy(alpha = 0.95f), Color(0xFF1A2030))),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = w * 0.42f,
                center = Offset(cx, cy),
            )
            when (style) {
                ProfileAvatarPresets.Style.AZURE_DRAKE -> {
                    val path = Path().apply {
                        moveTo(cx - w * 0.28f, cy + h * 0.1f)
                        quadraticTo(cx - w * 0.05f, cy - h * 0.35f, cx + w * 0.32f, cy - h * 0.05f)
                        quadraticTo(cx + w * 0.1f, cy + h * 0.05f, cx + w * 0.22f, cy + h * 0.28f)
                        quadraticTo(cx, cy + h * 0.18f, cx - w * 0.28f, cy + h * 0.1f)
                        close()
                    }
                    drawPath(path, accent)
                    drawCircle(ink, radius = w * 0.035f, center = Offset(cx + w * 0.12f, cy - h * 0.08f))
                }
                ProfileAvatarPresets.Style.ARCANE_ORB -> {
                    drawCircle(accent.copy(alpha = 0.85f), radius = w * 0.22f, center = Offset(cx, cy))
                    drawArc(
                        color = accent,
                        startAngle = -40f,
                        sweepAngle = 200f,
                        useCenter = false,
                        topLeft = Offset(cx - w * 0.3f, cy - h * 0.3f),
                        size = Size(w * 0.6f, h * 0.6f),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
                ProfileAvatarPresets.Style.STAR_DUELIST -> {
                    val star = Path()
                    val rOuter = w * 0.28f
                    val rInner = w * 0.12f
                    for (i in 0 until 10) {
                        val ang = Math.toRadians((-90 + i * 36).toDouble())
                        val r = if (i % 2 == 0) rOuter else rInner
                        val x = cx + (cos(ang) * r).toFloat()
                        val y = cy + (sin(ang) * r).toFloat()
                        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                    }
                    star.close()
                    drawPath(star, accent)
                }
                ProfileAvatarPresets.Style.ROSE_KNIGHT -> {
                    drawCircle(accent.copy(alpha = 0.9f), radius = w * 0.16f, center = Offset(cx, cy - h * 0.02f))
                    for (i in 0 until 6) {
                        val ang = Math.toRadians((i * 60).toDouble())
                        val x = cx + (cos(ang) * w * 0.22f).toFloat()
                        val y = cy + (sin(ang) * h * 0.22f).toFloat()
                        drawCircle(accent.copy(alpha = 0.75f), radius = w * 0.08f, center = Offset(x, y))
                    }
                    drawLine(
                        accent,
                        Offset(cx, cy + h * 0.12f),
                        Offset(cx, cy + h * 0.32f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                ProfileAvatarPresets.Style.VOID_WARRIOR -> {
                    val blade = Path().apply {
                        moveTo(cx, cy - h * 0.32f)
                        lineTo(cx + w * 0.08f, cy + h * 0.05f)
                        lineTo(cx, cy + h * 0.28f)
                        lineTo(cx - w * 0.08f, cy + h * 0.05f)
                        close()
                    }
                    drawPath(blade, accent)
                    drawCircle(ink, radius = w * 0.06f, center = Offset(cx, cy))
                }
                ProfileAvatarPresets.Style.GOLDEN_SHIELD -> {
                    val shield = Path().apply {
                        moveTo(cx, cy - h * 0.3f)
                        lineTo(cx + w * 0.26f, cy - h * 0.12f)
                        lineTo(cx + w * 0.22f, cy + h * 0.12f)
                        quadraticTo(cx, cy + h * 0.34f, cx - w * 0.22f, cy + h * 0.12f)
                        lineTo(cx - w * 0.26f, cy - h * 0.12f)
                        close()
                    }
                    drawPath(shield, accent.copy(alpha = 0.9f))
                    drawPath(shield, ink.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx()))
                }
                ProfileAvatarPresets.Style.PHOENIX_CREST -> {
                    val wing = Path().apply {
                        moveTo(cx, cy + h * 0.15f)
                        quadraticTo(cx - w * 0.35f, cy - h * 0.05f, cx - w * 0.1f, cy - h * 0.28f)
                        quadraticTo(cx - w * 0.05f, cy - h * 0.05f, cx, cy + h * 0.15f)
                        close()
                    }
                    drawPath(wing, accent)
                    val wing2 = Path().apply {
                        moveTo(cx, cy + h * 0.15f)
                        quadraticTo(cx + w * 0.35f, cy - h * 0.05f, cx + w * 0.1f, cy - h * 0.28f)
                        quadraticTo(cx + w * 0.05f, cy - h * 0.05f, cx, cy + h * 0.15f)
                        close()
                    }
                    drawPath(wing2, accent.copy(alpha = 0.85f))
                    drawCircle(gold, radius = w * 0.05f, center = Offset(cx, cy - h * 0.02f))
                }
                ProfileAvatarPresets.Style.ICE_SERPENT -> {
                    val coil = Path().apply {
                        moveTo(cx - w * 0.28f, cy + h * 0.2f)
                        cubicTo(
                            cx - w * 0.1f, cy - h * 0.25f,
                            cx + w * 0.15f, cy + h * 0.3f,
                            cx + w * 0.3f, cy - h * 0.15f,
                        )
                    }
                    drawPath(
                        coil,
                        accent,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCircle(accent, radius = w * 0.06f, center = Offset(cx + w * 0.3f, cy - h * 0.15f))
                }
            }
        }
    }
}
