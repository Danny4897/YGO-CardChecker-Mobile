package com.ygochecker.core.domain

import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCheckAppUpdateTest {
    @Test
    fun disabledWhenFeedBlank() = runBlocking {
        val check = DefaultCheckAppUpdate(FakeRepo(feedUrl = "", remote = null))
        assertTrue(check.invoke(1) is AppUpdateCheck.Disabled)
    }

    @Test
    fun availableWhenRemoteNewer() = runBlocking {
        val remote = AppUpdateManifest(2, "0.2.0", "https://x/a.apk", "notes")
        val check = DefaultCheckAppUpdate(FakeRepo(feedUrl = "https://x/update.json", remote = remote))
        val result = check.invoke(1)
        assertTrue(result is AppUpdateCheck.Available)
    }

    @Test
    fun skippedWhenUserDeferred() = runBlocking {
        val remote = AppUpdateManifest(2, "0.2.0", "https://x/a.apk")
        val check = DefaultCheckAppUpdate(
            FakeRepo(feedUrl = "https://x/update.json", remote = remote, skipped = 2),
        )
        assertTrue(check.invoke(1) is AppUpdateCheck.Skipped)
    }

    @Test
    fun ignoreSkipReoffersDeferred() = runBlocking {
        val remote = AppUpdateManifest(2, "0.2.0", "https://x/a.apk")
        val check = DefaultCheckAppUpdate(
            FakeRepo(feedUrl = "https://x/update.json", remote = remote, skipped = 2),
        )
        assertTrue(check.invoke(1, ignoreSkip = true) is AppUpdateCheck.Available)
    }

    private class FakeRepo(
        override val feedUrl: String,
        private val remote: AppUpdateManifest?,
        private val skipped: Int = 0,
    ) : AppUpdateRepository {
        override suspend fun fetchManifest(): AppResult<AppUpdateManifest> =
            if (remote == null) AppResult.Err(AppError("update.network")) else AppResult.Ok(remote)
        override suspend fun skippedVersionCode() = skipped
        override suspend fun setSkippedVersionCode(versionCode: Int) = Unit
        override suspend fun lastSeenVersionCode() = 0
        override suspend fun setLastSeenVersionCode(versionCode: Int) = Unit
    }
}
