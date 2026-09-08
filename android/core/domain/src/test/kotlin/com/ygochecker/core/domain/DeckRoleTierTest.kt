package com.ygochecker.core.domain

import com.ygochecker.core.model.HatCardRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckRoleTierTest {

    @Test
    fun `hat role wins over text role`() {
        assertEquals(RoleTier.CORE, resolveRoleTier(setOf("tech"), listOf(HatCardRole.ENGINE_STARTER)))
        assertEquals(RoleTier.TECH, resolveRoleTier(setOf("starter"), listOf(HatCardRole.SIDE_TECH)))
    }

    @Test
    fun `falls back to text role when no hat role matches`() {
        assertEquals(RoleTier.CORE, resolveRoleTier(setOf("starter"), emptyList()))
        assertEquals(RoleTier.SUPPORT, resolveRoleTier(setOf("extender"), emptyList()))
        assertEquals(RoleTier.TECH, resolveRoleTier(setOf("tech"), emptyList()))
    }

    @Test
    fun `defaults to support for unknown roles`() {
        assertEquals(RoleTier.SUPPORT, resolveRoleTier(emptySet(), emptyList()))
    }

    @Test
    fun `keeps formatMax below the tier taper threshold`() {
        assertEquals(3, scaledMaxCopies(3, RoleTier.CORE, 0.5))
        assertEquals(3, scaledMaxCopies(3, RoleTier.TECH, 0.3))
    }

    @Test
    fun `tapers toward 1 as fullness approaches 1 above threshold`() {
        assertEquals(3, scaledMaxCopies(3, RoleTier.TECH, 0.45))
        assertEquals(1, scaledMaxCopies(3, RoleTier.TECH, 1.0))
        val mid = scaledMaxCopies(3, RoleTier.TECH, 0.725)
        assertTrue(mid in 1..2)
    }

    @Test
    fun `never scales below 1 copy`() {
        assertEquals(1, scaledMaxCopies(3, RoleTier.CORE, 1.0))
    }

    @Test
    fun `passes through formatMax of 1 unchanged`() {
        assertEquals(1, scaledMaxCopies(1, RoleTier.TECH, 1.0))
        assertEquals(1, scaledMaxCopies(1, RoleTier.CORE, 0.0))
    }

    @Test
    fun `core tier tapers later than tech tier at the same fullness`() {
        val fullness = 0.75
        assertTrue(scaledMaxCopies(3, RoleTier.CORE, fullness) > scaledMaxCopies(3, RoleTier.TECH, fullness))
    }
}
