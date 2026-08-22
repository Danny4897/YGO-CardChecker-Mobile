package com.ygochecker.core.model

object MdproAssetPaths {
    fun cardArt(passcode: Int): String = "Picture/Art/$passcode.jpg"
    fun closeup(passcode: Int): String = "Picture/Closeup/$passcode.jpg"
}
