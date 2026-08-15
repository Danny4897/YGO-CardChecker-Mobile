package com.ygochecker.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact non-blocking working chip (complete-deck / long jobs).
 * Does not cover the screen — user can keep editing / navigating.
 */
@Composable
fun DuelWorkingBanner(
    title: String = stringResource(R.string.complete_working_title),
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "duel-work-banner")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val gold = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF01A1F2C),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(Modifier.size(22.dp)) {
                val r = size.minDimension / 2f
                rotate(spin) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, gold, gold.copy(alpha = 0.25f)),
                        ),
                        startAngle = 0f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(size.width / 2f - r * 0.85f, size.height / 2f - r * 0.85f),
                        size = Size(r * 1.7f, r * 1.7f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                drawCircle(gold, radius = 2.dp.toPx())
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** @deprecated Prefer [DuelWorkingBanner] for non-blocking jobs. */
@Deprecated("Use DuelWorkingBanner", ReplaceWith("DuelWorkingBanner(title)"))
@Composable
fun DuelWorkingOverlay(
    title: String,
    subtitle: String = stringResource(R.string.complete_working_hint),
    modifier: Modifier = Modifier,
) {
    DuelWorkingBanner(title = title, modifier = modifier)
}
