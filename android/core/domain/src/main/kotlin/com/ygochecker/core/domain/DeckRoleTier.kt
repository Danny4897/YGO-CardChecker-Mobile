package com.ygochecker.core.domain

import com.ygochecker.core.model.HatCardRole
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mirrors ygo-card-checker's deck-role-tier.utils.ts: suggested copies taper toward 1
 * as a deck section fills, with core engine pieces staying near max copies longer than
 * tech/situational picks.
 */
enum class RoleTier { CORE, SUPPORT, TECH }

private val HAT_ROLE_TIER: Map<HatCardRole, RoleTier> = mapOf(
    HatCardRole.ENGINE_STARTER to RoleTier.CORE,
    HatCardRole.SEARCHER to RoleTier.CORE,
    HatCardRole.EXTENDER to RoleTier.SUPPORT,
    HatCardRole.RECYCLE to RoleTier.SUPPORT,
    HatCardRole.TRAP_LINE to RoleTier.SUPPORT,
    HatCardRole.INTERRUPT to RoleTier.SUPPORT,
    HatCardRole.BAIT to RoleTier.TECH,
    HatCardRole.REMOVAL to RoleTier.TECH,
    HatCardRole.SIDE_TECH to RoleTier.TECH,
)

private val TEXT_ROLE_TIER: Map<String, RoleTier> = mapOf(
    "starter" to RoleTier.CORE,
    "engine" to RoleTier.CORE,
    "extender" to RoleTier.SUPPORT,
    "draw" to RoleTier.SUPPORT,
    "handtrap" to RoleTier.SUPPORT,
    "board_breaker" to RoleTier.SUPPORT,
    "trap" to RoleTier.TECH,
    "tech" to RoleTier.TECH,
)

/** Fullness fraction (current/target) at which suggested copies start tapering toward 1. */
private val TIER_TAPER_THRESHOLD: Map<RoleTier, Double> = mapOf(
    RoleTier.CORE to 0.85,
    RoleTier.SUPPORT to 0.7,
    RoleTier.TECH to 0.45,
)

/** HAT format roles win when present; otherwise fall back to the effect-text profiler roles. */
fun resolveRoleTier(textRoles: Set<String>, hatRoles: List<HatCardRole>): RoleTier {
    for (hatRole in hatRoles) {
        HAT_ROLE_TIER[hatRole]?.let { return it }
    }
    for (role in textRoles) {
        TEXT_ROLE_TIER[role]?.let { return it }
    }
    return RoleTier.SUPPORT
}

/**
 * Max copies to suggest given how full the deck section already is.
 * Below the tier's taper threshold, stays at formatMax; above it, interpolates
 * linearly down to 1 copy as fullness approaches 1 (section complete).
 */
fun scaledMaxCopies(formatMax: Int, tier: RoleTier, fullness: Double): Int {
    if (formatMax <= 1) return formatMax
    val threshold = TIER_TAPER_THRESHOLD.getValue(tier)
    if (fullness <= threshold) return formatMax
    val t = min(1.0, (fullness - threshold) / (1.0 - threshold))
    val scaled = formatMax - t * (formatMax - 1)
    return scaled.roundToInt().coerceAtLeast(1)
}
