package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult
import com.ygochecker.core.model.CatalogSyncProgress
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.OfflinePackStatus
import com.ygochecker.core.model.RelatedCardRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pack loaded: missing format row = 0 (Adamancipator in HAT, Scythe in TCG, …).
 * Pack empty: stubMax fallback.
 */
class EvaluateDeckLegalityFormatTest {
    private val scytheId = 20292186
    private val blueEyesId = 89631139
    private val adamId = 11302671

    @Test
    fun `known card missing from format is forbidden`() {
        val pack = FakePack(
            limits = mapOf(
                scytheId to mapOf(GameFormat.HAT to 3),
                blueEyesId to mapOf(GameFormat.TCG to 3, GameFormat.HAT to 3),
                adamId to mapOf(GameFormat.TCG to 3),
            ),
        )
        val legality = DefaultEvaluateDeckLegality(pack)

        assertEquals(3, legality.maxCopies(scytheId, GameFormat.HAT))
        assertEquals(0, legality.maxCopies(scytheId, GameFormat.TCG))
        assertEquals(3, legality.maxCopies(blueEyesId, GameFormat.TCG))
        assertEquals(0, legality.maxCopies(adamId, GameFormat.HAT))
    }

    @Test
    fun `card absent from pack entirely is forbidden when pack loaded`() {
        val pack = FakePack(limits = mapOf(blueEyesId to mapOf(GameFormat.TCG to 3)))
        val legality = DefaultEvaluateDeckLegality(pack)
        assertEquals(0, legality.maxCopies(99999999, GameFormat.TCG))
        assertEquals(0, legality.maxCopies(99999999, GameFormat.HAT))
    }

    @Test
    fun `empty pack falls back to stub`() {
        val pack = FakePack(limits = emptyMap())
        val legality = DefaultEvaluateDeckLegality(pack)
        assertEquals(0, legality.maxCopies(55144522, GameFormat.TCG))
        assertEquals(3, legality.maxCopies(99999999, GameFormat.TCG))
    }
}

private class FakePack(
    private val limits: Map<Int, Map<GameFormat, Int>>,
) : OfflinePackRepository {
    override fun status(): Flow<OfflinePackStatus> = emptyFlow()
    override fun progress(): Flow<CatalogSyncProgress?> = emptyFlow()
    override fun clearProgress() = Unit
    override suspend fun ensureBundledKnowledge() = Unit
    override suspend fun ensureRelatedKnowledge() = Unit
    override suspend fun syncCatalog(): AppResult<Int> = error("not used")
    override suspend fun syncFormatLegality(): AppResult<Int> = error("not used")
    override suspend fun syncEffectScripts(fullRemote: Boolean): AppResult<Int> = error("not used")
    override suspend fun syncRelated(): AppResult<Int> = error("not used")
    override suspend fun syncAllKnowledge(): AppResult<Int> = error("not used")
    override suspend fun enrichScriptsFromCardText(): AppResult<Int> = error("not used")
    override fun maxCopiesOrNull(cardId: Int, format: GameFormat): Int? {
        if (limits.isEmpty()) return null
        return limits[cardId]?.get(format) ?: 0
    }
    override suspend fun effectScriptCount(): Int = 0
    override suspend fun effectScript(cardId: Int): EffectScriptSummary? = null
    override suspend fun effectScripts(ids: Collection<Int>): List<EffectScriptSummary> = emptyList()
    override suspend fun relatedCards(cardId: Int, limit: Int): List<RelatedCardRef> = emptyList()
}
