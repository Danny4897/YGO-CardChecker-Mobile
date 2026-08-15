package com.ygochecker.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * App entrance: stylized Duel Disk opens + hologram field expands.
 * Drawn with Canvas only — no launcher PNG overlay.
 */
@Composable
fun DuelEntranceSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    brand: String = stringResource(R.string.splash_brand),
    tagline: String = stringResource(R.string.splash_tagline),
    holdMs: Long = 1100L,
    /** @deprecated Use [DuelDeckForgeSplash] for import. Ignored here. */
    forgeMode: Boolean = false,
    /** @deprecated PNG logos are no longer shown. */
    logo: @Composable () -> Unit = {},
) {
    if (forgeMode) {
        DuelDeckForgeSplash(
            onFinished = onFinished,
            modifier = modifier,
            brand = brand,
            tagline = tagline,
            holdMs = holdMs,
        )
        return
    }

    val open = remember { Animatable(0f) }
    val holo = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        open.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        launch { holo.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        delay(280)
        title.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        delay(holdMs)
        exit.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        onFinished()
    }

    val gold = MaterialTheme.colorScheme.primary
    val goldSoft = MaterialTheme.colorScheme.primaryContainer
    val ink = Color(0xFF0E121C)
    val ivory = MaterialTheme.colorScheme.onBackground
    val openAmt = open.value
    val holoAmt = holo.value

    Box(
        modifier
            .fillMaxSize()
            .alpha(exit.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A2238), ink),
                    radius = 1100f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.46f
            val diskR = size.minDimension * 0.22f

            // Expanding hologram rings + spokes
            if (holoAmt > 0.01f) {
                val maxR = size.minDimension * 0.62f
                for (i in 0 until 4) {
                    val t = ((holoAmt * 1.15f) - i * 0.12f).coerceIn(0f, 1f)
                    val r = diskR * 0.55f + (maxR - diskR * 0.55f) * t
                    drawCircle(
                        color = gold.copy(alpha = (0.45f - i * 0.08f) * t),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = (2.2f - i * 0.3f).dp.toPx()),
                    )
                }
                val spokes = 12
                for (s in 0 until spokes) {
                    val ang = (s / spokes.toFloat()) * Math.PI.toFloat() * 2f + holoAmt * 0.35f
                    val inner = diskR * 0.7f
                    val outer = diskR * 0.7f + (maxR - diskR) * holoAmt * 0.85f
                    drawLine(
                        color = gold.copy(alpha = 0.18f * holoAmt),
                        start = Offset(cx + cos(ang) * inner, cy + sin(ang) * inner),
                        end = Offset(cx + cos(ang) * outer, cy + sin(ang) * outer),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // Soft hologram wash
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            gold.copy(alpha = 0.22f * holoAmt),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = maxR * holoAmt,
                    ),
                    radius = maxR * holoAmt,
                    center = Offset(cx, cy),
                )
            }

            // Duel Disk body (base)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF2A3144), Color(0xFF12161F)),
                    center = Offset(cx, cy),
                    radius = diskR,
                ),
                radius = diskR,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = gold.copy(alpha = 0.85f),
                radius = diskR,
                center = Offset(cx, cy),
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = goldSoft.copy(alpha = 0.5f),
                radius = diskR * 0.72f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Opening lids (left / right halves swing open)
            val lidOpenDeg = 78f * openAmt
            val lidW = diskR * 0.95f
            val lidH = diskR * 1.55f
            fun drawLid(swing: Float, hingeX: Float) {
                withTransform({
                    translate(hingeX, cy)
                    rotate(swing, pivot = Offset.Zero)
                }) {
                    val left = -lidW * 0.08f
                    val top = -lidH / 2f
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF3A4258), Color(0xFF1A2030)),
                        ),
                        topLeft = Offset(left, top),
                        size = Size(lidW, lidH),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    )
                    drawRoundRect(
                        color = gold.copy(alpha = 0.7f),
                        topLeft = Offset(left, top),
                        size = Size(lidW, lidH),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    // Slot glow on inner edge
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, gold.copy(alpha = 0.9f), Color.Transparent),
                        ),
                        start = Offset(left + 4.dp.toPx(), top + 10.dp.toPx()),
                        end = Offset(left + 4.dp.toPx(), top + lidH - 10.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            drawLid(-lidOpenDeg, cx - diskR * 0.15f)
            drawLid(lidOpenDeg, cx + diskR * 0.15f)

            // Center crystal / deck seal
            val crystal = diskR * 0.22f * (0.55f + 0.45f * openAmt)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(gold.copy(alpha = 0.95f), goldSoft.copy(alpha = 0.35f)),
                    center = Offset(cx, cy),
                    radius = crystal,
                ),
                radius = crystal,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = ivory.copy(alpha = 0.35f * openAmt),
                radius = crystal * 0.35f,
                center = Offset(cx - crystal * 0.2f, cy - crystal * 0.25f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .alpha(title.value),
        ) {
            Text(
                text = brand,
                style = MaterialTheme.typography.headlineMedium,
                color = gold,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = ivory.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

/**
 * Import celebration: deck shuffles, cards fly in and stack.
 * Stylized card backs only — no card artwork.
 */
@Composable
fun DuelDeckForgeSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    brand: String = stringResource(R.string.import_forge_title),
    tagline: String = stringResource(R.string.import_forge_tagline),
    holdMs: Long = 1000L,
) {
    val shuffle = remember { Animatable(0f) }
    val stack = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        shuffle.animateTo(1f, tween(900, easing = LinearEasing))
        stack.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        title.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(holdMs)
        exit.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        onFinished()
    }

    val gold = MaterialTheme.colorScheme.primary
    val goldSoft = MaterialTheme.colorScheme.primaryContainer
    val ink = Color(0xFF0E121C)
    val ivory = MaterialTheme.colorScheme.onBackground
    val sh = shuffle.value
    val st = stack.value

    Box(
        modifier
            .fillMaxSize()
            .alpha(exit.value)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1E2433), ink),
                    radius = 1000f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.42f
            val cardW = 72.dp.toPx()
            val cardH = 104.dp.toPx()
            val count = 7

            fun drawCardBack(center: Offset, rotationDeg: Float, alpha: Float, lift: Float) {
                rotate(rotationDeg, pivot = center) {
                    val tl = Offset(center.x - cardW / 2f, center.y - cardH / 2f - lift)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF1A2238), Color(0xFF0A0E16)),
                        ),
                        topLeft = tl,
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        alpha = alpha,
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(gold, goldSoft, gold)),
                        topLeft = tl,
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                        alpha = alpha,
                    )
                    val inset = 10.dp.toPx()
                    drawRoundRect(
                        color = gold.copy(alpha = 0.35f * alpha),
                        topLeft = Offset(tl.x + inset, tl.y + inset),
                        size = Size(cardW - inset * 2f, cardH - inset * 2f),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    // Center diamond seal
                    val d = 10.dp.toPx()
                    val mid = Offset(center.x, center.y - lift)
                    val path = Path().apply {
                        moveTo(mid.x, mid.y - d)
                        lineTo(mid.x + d, mid.y)
                        lineTo(mid.x, mid.y + d)
                        lineTo(mid.x - d, mid.y)
                        close()
                    }
                    drawPath(path, color = gold.copy(alpha = 0.75f * alpha), style = Stroke(2.dp.toPx()))
                }
            }

            // Shuffle swirl
            for (i in 0 until count) {
                val phase = (i / count.toFloat())
                val orbit = (1f - st).coerceIn(0f, 1f)
                val angle = phase * Math.PI.toFloat() * 2f + sh * Math.PI.toFloat() * 2.4f
                val radius = 110.dp.toPx() * (0.35f + 0.65f * orbit) * (0.4f + 0.6f * sh)
                val x = cx + cos(angle) * radius * orbit
                val y = cy + sin(angle) * radius * 0.55f * orbit
                val rot = (i * 18f - 40f) * orbit + sin(sh * Math.PI.toFloat() * 2f + phase) * 25f * orbit
                val a = (0.45f + 0.55f * sh) * (0.35f + 0.65f * orbit)
                drawCardBack(Offset(x, y), rot, a.coerceIn(0f, 1f), 0f)
            }

            // Cards settling into a neat stack
            val stackCount = 5
            for (i in 0 until stackCount) {
                val appear = ((st * stackCount) - i).coerceIn(0f, 1f)
                if (appear <= 0f) continue
                val fromAngle = (i / stackCount.toFloat()) * Math.PI.toFloat() * 2f + 1.2f
                val fromR = 160.dp.toPx() * (1f - appear)
                val x = cx + cos(fromAngle) * fromR
                val y = cy + sin(fromAngle) * fromR * 0.4f - i * 2.5.dp.toPx() * appear
                val rot = (1f - appear) * (40f - i * 8f)
                drawCardBack(Offset(x, y), rot, appear, i * 1.5.dp.toPx() * appear)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
                .alpha(title.value),
        ) {
            Text(
                text = brand,
                style = MaterialTheme.typography.headlineMedium,
                color = gold,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = ivory.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}
