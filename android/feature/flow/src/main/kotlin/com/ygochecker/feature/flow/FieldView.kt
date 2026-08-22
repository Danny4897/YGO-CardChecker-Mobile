package com.ygochecker.feature.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.FieldArea
import com.ygochecker.core.model.FieldOwner
import com.ygochecker.core.model.FieldZone
import com.ygochecker.core.model.MdproAssetPaths
import com.ygochecker.core.model.PuzzleBoardSlot
import java.io.File

internal fun mdproOrFallback(root: String?, cardId: Int, fallbackUrl: String): Any {
    if (root.isNullOrBlank()) return fallbackUrl
    val file = File(root, MdproAssetPaths.cardArt(cardId))
    return if (file.isFile) file else fallbackUrl
}

@Composable
internal fun FieldView(
    board: List<PuzzleBoardSlot>,
    cardsById: Map<Int, Card>,
    mdproRoot: String?,
    chain: List<Int>,
    pending: List<Int>,
    onTapPending: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mat = Color(0xFF0B1220)
    Column(
        modifier
            .fillMaxSize()
            .background(mat)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ZoneRow(
            owner = FieldOwner.OPP,
            board = board,
            cardsById = cardsById,
            mdproRoot = mdproRoot,
        )
        ChainStrip(chain = chain, cardsById = cardsById, mdproRoot = mdproRoot)
        ZoneRow(
            owner = FieldOwner.YOU,
            board = board,
            cardsById = cardsById,
            mdproRoot = mdproRoot,
        )
        if (pending.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                pending.forEach { id ->
                    CardSlot(
                        cardId = id,
                        card = cardsById[id],
                        mdproRoot = mdproRoot,
                        highlight = true,
                        onClick = { onTapPending(id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(
    owner: FieldOwner,
    board: List<PuzzleBoardSlot>,
    cardsById: Map<Int, Card>,
    mdproRoot: String?,
) {
    fun slot(area: FieldArea, index: Int = 0) =
        board.firstOrNull { it.zone.owner == owner && it.zone.area == area && it.zone.index == index }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardSlot(slot(FieldArea.GY)?.cardId, cardsById[slot(FieldArea.GY)?.cardId], mdproRoot)
        repeat(5) { i ->
            CardSlot(
                slot(FieldArea.MONSTER, i + 1)?.cardId,
                cardsById[slot(FieldArea.MONSTER, i + 1)?.cardId],
                mdproRoot,
            )
        }
        CardSlot(slot(FieldArea.BANISH)?.cardId, cardsById[slot(FieldArea.BANISH)?.cardId], mdproRoot)
    }
}

@Composable
private fun ChainStrip(
    chain: List<Int>,
    cardsById: Map<Int, Card>,
    mdproRoot: String?,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chain.isEmpty()) {
            Text("CL", color = Color(0xFF7FD4FF), style = MaterialTheme.typography.labelMedium)
        } else {
            chain.forEachIndexed { index, id ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CL${index + 1}", color = Color(0xFF7FD4FF), style = MaterialTheme.typography.labelSmall)
                    CardSlot(id, cardsById[id], mdproRoot, compact = true)
                }
                if (index != chain.lastIndex) Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun CardSlot(
    cardId: Int?,
    card: Card?,
    mdproRoot: String?,
    highlight: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val w = if (compact) 36.dp else 44.dp
    val h = if (compact) 52.dp else 64.dp
    val shape = RoundedCornerShape(4.dp)
    val border = if (highlight) Color(0xFFFFD54A) else Color(0xFF3A5A7A)
    Box(
        Modifier
            .size(w, h)
            .clip(shape)
            .border(1.dp, border, shape)
            .background(Color(0xFF152033))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (cardId != null) {
            val fallback = card?.imageUrl.orEmpty()
            val model = mdproOrFallback(mdproRoot, cardId, fallback)
            if (model != "") {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(model).build(),
                    contentDescription = card?.name ?: cardId.toString(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = card?.name ?: cardId.toString(),
                color = Color.White,
                fontSize = if (compact) 7.sp else 8.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .padding(2.dp),
            )
        }
    }
}
