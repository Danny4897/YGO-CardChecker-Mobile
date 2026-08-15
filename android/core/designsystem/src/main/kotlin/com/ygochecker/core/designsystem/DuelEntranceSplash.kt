package com.ygochecker.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Duel entrance / celebration: card fan (or forge stack) + gold slash + emblem.
 * Stylized only — no Konami character or card art.
 */
@Composable
fun DuelEntranceSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    brand: String = stringResource(R.string.splash_brand),
    tagline: String = stringResource(R.string.splash_tagline),
    holdMs: Long = 980L,
    /** Cards gather into a stacked deck (import / forge), then reveal. */
    forgeMode: Boolean = false,
    logo: @Composable () -> Unit = { DuelistEmblem() },
) {
    val fan = remember { Animatable(0f) }
    val forge = remember { Animatable(0f) }
    val slash = remember { Animatable(0f) }
    val emblem = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        fan.animateTo(1f, tween(if (forgeMode) 720 else 680, easing = FastOutSlowInEasing))
        if (forgeMode) {
            forge.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
            delay(80)
        }
        launch { slash.animateTo(1f, tween(if (forgeMode) 480 else 400, easing = LinearEasing)) }
        delay(if (forgeMode) 220 else 160)
        emblem.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        title.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        delay(holdMs)
        exit.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        onFinished()
    }

    val gold = MaterialTheme.colorScheme.primary
    val goldSoft = MaterialTheme.colorScheme.primaryContainer
    val ink = Color(0xFF151823)
    val ivory = MaterialTheme.colorScheme.onBackground
    val collapse = if (forgeMode) forge.value else 0f
    val spread = (1f - collapse).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxSize()
            .alpha(exit.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E2433), ink),
                    radius = 900f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val progress = slash.value
            if (progress > 0f) {
                val y = size.height * 0.42f
                val startX = -size.width * 0.2f
                val endX = size.width * 1.2f
                val x = startX + (endX - startX) * progress
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, gold.copy(alpha = 0.85f), Color.Transparent),
                    ),
                    start = Offset(x - size.width * 0.35f, y),
                    end = Offset(x, y),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Box(contentAlignment = Alignment.Center) {
            DuelSplashCard(
                rotation = -18f * spread,
                xOffset = (-56).dp * fan.value * spread,
                yOffset = 28.dp * (1f - fan.value) + 6.dp * collapse,
                alpha = (0.55f + 0.35f * collapse) * fan.value,
                gold = gold,
                goldSoft = goldSoft,
            )
            DuelSplashCard(
                rotation = 18f * spread,
                xOffset = 56.dp * fan.value * spread,
                yOffset = 28.dp * (1f - fan.value) + 3.dp * collapse,
                alpha = (0.55f + 0.35f * collapse) * fan.value,
                gold = gold,
                goldSoft = goldSoft,
            )
            DuelSplashCard(
                rotation = 0f,
                xOffset = 0.dp,
                yOffset = 40.dp * (1f - fan.value),
                alpha = 0.95f * fan.value,
                gold = gold,
                goldSoft = goldSoft,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(0.72f + 0.28f * emblem.value)
                    .alpha(emblem.value),
            ) {
                Box(
                    Modifier
                        .size(132.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    logo()
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = brand,
                    style = MaterialTheme.typography.headlineMedium,
                    color = gold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(title.value),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ivory.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .alpha(title.value),
                )
            }
        }
    }
}

/** Stylized duelist silhouette (generic — not a licensed character). */
@Composable
fun DuelistEmblem(modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.primary
    val goldSoft = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.size(120.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        // Spiky hair crown
        val hair = Path().apply {
            moveTo(cx, h * 0.06f)
            lineTo(cx - w * 0.22f, h * 0.28f)
            lineTo(cx - w * 0.12f, h * 0.22f)
            lineTo(cx - w * 0.32f, h * 0.42f)
            lineTo(cx - w * 0.08f, h * 0.34f)
            lineTo(cx, h * 0.48f)
            lineTo(cx + w * 0.08f, h * 0.34f)
            lineTo(cx + w * 0.32f, h * 0.42f)
            lineTo(cx + w * 0.12f, h * 0.22f)
            lineTo(cx + w * 0.22f, h * 0.28f)
            close()
        }
        drawPath(
            hair,
            brush = Brush.verticalGradient(listOf(gold, goldSoft)),
        )
        // Face oval
        drawOval(
            color = Color(0xFF1A2238),
            topLeft = Offset(cx - w * 0.14f, h * 0.30f),
            size = Size(w * 0.28f, h * 0.30f),
        )
        drawOval(
            color = gold.copy(alpha = 0.9f),
            topLeft = Offset(cx - w * 0.14f, h * 0.30f),
            size = Size(w * 0.28f, h * 0.30f),
            style = Stroke(width = 2.5.dp.toPx()),
        )
        // Pendant / deck-seal geometric
        val seal = Path().apply {
            val sy = h * 0.72f
            moveTo(cx, sy - h * 0.08f)
            lineTo(cx + w * 0.10f, sy)
            lineTo(cx, sy + h * 0.08f)
            lineTo(cx - w * 0.10f, sy)
            close()
        }
        drawPath(seal, color = gold, style = Stroke(width = 3.dp.toPx()))
        drawCircle(gold, radius = 4.dp.toPx(), center = Offset(cx, h * 0.72f))
    }
}

@Composable
private fun DuelSplashCard(
    rotation: Float,
    xOffset: Dp,
    yOffset: Dp,
    alpha: Float,
    gold: Color,
    goldSoft: Color,
) {
    Box(
        Modifier
            .offset(x = xOffset, y = yOffset)
            .rotate(rotation)
            .alpha(alpha)
            .width(92.dp)
            .height(132.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A2238), Color(0xFF0E121C)),
                ),
                RoundedCornerShape(10.dp),
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(gold, goldSoft, gold)),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, gold.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .background(
                    Brush.radialGradient(
                        listOf(gold.copy(alpha = 0.18f), Color.Transparent),
                    ),
                    RoundedCornerShape(6.dp),
                ),
        )
    }
}
