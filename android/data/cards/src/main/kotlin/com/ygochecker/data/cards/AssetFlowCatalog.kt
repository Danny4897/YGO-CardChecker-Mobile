package com.ygochecker.data.cards

import android.content.Context
import com.ygochecker.core.domain.FlowCatalog
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowSummary
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.HatCardRole
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.toSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetFlowCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) : FlowCatalog {
    private val flowsCache = AtomicReference<List<FlowGraph>?>(null)
    private val rolesCache = AtomicReference<List<FormatCardRole>?>(null)

    override suspend fun list(format: GameFormat): List<FlowSummary> {
        val all = loadFlows()
        val match = all.filter { it.formatId == format.id }
        val chosen = if (match.isNotEmpty()) match else all.filter { it.formatId == GameFormat.HAT.id }
        return chosen.map { it.toSummary() }
    }

    override suspend fun get(flowId: String): FlowGraph? =
        loadFlows().firstOrNull { it.id == flowId }

    override suspend fun graphs(format: GameFormat): List<FlowGraph> {
        val all = loadFlows()
        val match = all.filter { it.formatId == format.id }
        return if (match.isNotEmpty()) match else all.filter { it.formatId == GameFormat.HAT.id }
    }

    override fun rolesFor(cardId: Int, format: GameFormat): List<FormatCardRole> =
        loadRoles().filter { it.cardId == cardId && it.formatId == format.id }

    override fun allRoles(format: GameFormat): List<FormatCardRole> =
        loadRoles().filter { it.formatId == format.id }

    /** Partner cards from HAT tactical roles — high score for CompleteDeck / Related. */
    fun hatRoleRelated(cardId: Int, nameById: (Int) -> String?): List<RelatedCardRef> {
        val roles = rolesFor(cardId, GameFormat.HAT)
        if (roles.isEmpty()) return emptyList()
        val out = linkedMapOf<Int, RelatedCardRef>()
        for (role in roles) {
            val relation = role.role.toRelation()
            val score = role.role.baseScore() + role.priority * 0.05
            for (partnerId in role.partnerIds) {
                if (partnerId == cardId) continue
                val name = nameById(partnerId) ?: "Card $partnerId"
                val prev = out[partnerId]
                if (prev == null || score > prev.score) {
                    out[partnerId] = RelatedCardRef(
                        id = partnerId,
                        name = name,
                        relation = relation,
                        score = score,
                    )
                }
            }
        }
        return out.values.toList()
    }

    private fun loadFlows(): List<FlowGraph> {
        flowsCache.get()?.let { return it }
        return synchronized(flowsCache) {
            flowsCache.get()?.let { return it }
            val text = runCatching {
                context.assets.open(OfflinePackAssets.FLOWS_HAT).bufferedReader().use { it.readText() }
            }.getOrDefault("{\"flows\":[]}")
            val parsed = FlowPackParser.parseFlows(text)
            flowsCache.set(parsed)
            parsed
        }
    }

    private fun loadRoles(): List<FormatCardRole> {
        rolesCache.get()?.let { return it }
        return synchronized(rolesCache) {
            rolesCache.get()?.let { return it }
            val text = runCatching {
                context.assets.open(OfflinePackAssets.CARD_ROLES_HAT).bufferedReader().use { it.readText() }
            }.getOrDefault("{\"roles\":[]}")
            val parsed = FlowPackParser.parseRoles(text)
            rolesCache.set(parsed)
            parsed
        }
    }
}

private fun HatCardRole.toRelation(): String = when (this) {
    HatCardRole.ENGINE_STARTER -> "hat_engine_starter"
    HatCardRole.SEARCHER -> "hat_searcher"
    HatCardRole.REMOVAL -> "hat_removal"
    HatCardRole.BAIT -> "hat_bait"
    HatCardRole.INTERRUPT -> "hat_interrupt"
    HatCardRole.TRAP_LINE -> "hat_trap_line"
    HatCardRole.SIDE_TECH -> "hat_side_tech"
    HatCardRole.EXTENDER -> "hat_extender"
    HatCardRole.RECYCLE -> "hat_recycle"
}

private fun HatCardRole.baseScore(): Double = when (this) {
    HatCardRole.ENGINE_STARTER, HatCardRole.SEARCHER -> 2.9
    HatCardRole.INTERRUPT, HatCardRole.TRAP_LINE -> 2.6
    HatCardRole.REMOVAL, HatCardRole.BAIT -> 2.4
    HatCardRole.EXTENDER, HatCardRole.RECYCLE -> 2.1
    HatCardRole.SIDE_TECH -> 1.8
}
