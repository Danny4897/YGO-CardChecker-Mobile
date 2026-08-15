package com.ygochecker.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpAppUpdateRepositoryParseTest {
    @Test
    fun parsesValidManifest() {
        val json = """
            {"versionCode":3,"versionName":"0.3.0","apkUrl":"https://x/a.apk","changelog":"hi"}
        """.trimIndent()
        val m = HttpAppUpdateRepository.parseManifest(json)!!
        assertEquals(3, m.versionCode)
        assertEquals("0.3.0", m.versionName)
        assertEquals("https://x/a.apk", m.apkUrl)
        assertEquals("hi", m.changelog)
    }

    @Test
    fun rejectsIncomplete() {
        assertNull(HttpAppUpdateRepository.parseManifest("""{"versionCode":1}"""))
        assertNull(HttpAppUpdateRepository.parseManifest("not-json"))
    }
}
