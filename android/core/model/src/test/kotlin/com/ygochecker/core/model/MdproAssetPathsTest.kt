package com.ygochecker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MdproAssetPathsTest {
    @Test
    fun `card art matches MDPro3 Picture Art jpeg layout`() {
        assertEquals("Picture/Art/10000.jpg", MdproAssetPaths.cardArt(10000))
    }

    @Test
    fun `closeup path is under Picture Closeup`() {
        assertEquals("Picture/Closeup/10000.jpg", MdproAssetPaths.closeup(10000))
    }
}
