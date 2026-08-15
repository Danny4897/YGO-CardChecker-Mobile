package com.ygochecker.core.domain

import com.ygochecker.core.common.AppResult

/** Remote FOSS update feed (`update.json`). */
data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String = "",
)

sealed class AppUpdateCheck {
    /** Feed URL not configured — updater idle. */
    data object Disabled : AppUpdateCheck()
    data object UpToDate : AppUpdateCheck()
    /** User chose “Later” for this versionCode. */
    data object Skipped : AppUpdateCheck()
    data class Available(val manifest: AppUpdateManifest) : AppUpdateCheck()
    data class Failed(val errorKey: String) : AppUpdateCheck()
}

interface AppUpdateRepository {
    /** Empty string → checks are no-ops ([AppUpdateCheck.Disabled]). */
    val feedUrl: String
    suspend fun fetchManifest(): AppResult<AppUpdateManifest>
    suspend fun skippedVersionCode(): Int
    suspend fun setSkippedVersionCode(versionCode: Int)
    /** Last version whose “what’s new” dialog was shown. */
    suspend fun lastSeenVersionCode(): Int
    suspend fun setLastSeenVersionCode(versionCode: Int)
}

fun interface CheckAppUpdate {
    suspend fun invoke(installedVersionCode: Int): AppUpdateCheck
}

class DefaultCheckAppUpdate @javax.inject.Inject constructor(
    private val repo: AppUpdateRepository,
) : CheckAppUpdate {
    override suspend fun invoke(installedVersionCode: Int): AppUpdateCheck {
        if (repo.feedUrl.isBlank()) return AppUpdateCheck.Disabled
        return when (val result = repo.fetchManifest()) {
            is AppResult.Err -> AppUpdateCheck.Failed(result.error.errorKey)
            is AppResult.Ok -> {
                val remote = result.value
                when {
                    remote.versionCode <= installedVersionCode -> AppUpdateCheck.UpToDate
                    remote.versionCode <= repo.skippedVersionCode() -> AppUpdateCheck.Skipped
                    remote.apkUrl.isBlank() -> AppUpdateCheck.Failed("update.invalid")
                    else -> AppUpdateCheck.Available(remote)
                }
            }
        }
    }
}
