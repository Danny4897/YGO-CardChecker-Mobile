package com.ygochecker.android.update

import android.content.Context
import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.AppUpdateManifest
import com.ygochecker.core.domain.AppUpdateRepository
import com.ygochecker.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.ygochecker.data.deck.appPrefsStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpAppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) : AppUpdateRepository {
    private val skippedKey = intPreferencesKey("update_skipped_version_code")
    private val lastSeenKey = intPreferencesKey("whats_new_last_seen_version_code")

    override val feedUrl: String = BuildConfig.UPDATE_FEED_URL.trim()

    override suspend fun fetchManifest(): AppResult<AppUpdateManifest> = withContext(Dispatchers.IO) {
        val urls = feedCandidateUrls(feedUrl)
        if (urls.isEmpty()) return@withContext AppResult.Err(AppError("update.disabled"))
        val manifests = coroutineScope {
            urls.map { url ->
                async { fetchOne(url) }
            }.awaitAll()
        }.filterNotNull()
        val best = manifests.maxByOrNull { it.versionCode }
            ?: return@withContext AppResult.Err(AppError("update.network"))
        AppResult.Ok(best)
    }

    private fun fetchOne(base: String): AppUpdateManifest? {
        return try {
            val separator = if (base.contains('?')) '&' else '?'
            val url = "$base${separator}_=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                parseManifest(body)
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun skippedVersionCode(): Int =
        context.appPrefsStore.data.first()[skippedKey] ?: 0

    override suspend fun setSkippedVersionCode(versionCode: Int) {
        context.appPrefsStore.edit { it[skippedKey] = versionCode }
    }

    override suspend fun lastSeenVersionCode(): Int =
        context.appPrefsStore.data.first()[lastSeenKey] ?: 0

    override suspend fun setLastSeenVersionCode(versionCode: Int) {
        context.appPrefsStore.edit { it[lastSeenKey] = versionCode }
    }

    companion object {
        private const val RAW_FEED =
            "https://raw.githubusercontent.com/Danny4897/YGO-CardChecker-Mobile/main/android/distribution/update.json"
        private const val JSDELIVR_FEED =
            "https://cdn.jsdelivr.net/gh/Danny4897/YGO-CardChecker-Mobile@main/android/distribution/update.json"

        /** Primary + mirrors; caller picks highest versionCode (jsDelivr @main can lag). */
        fun feedCandidateUrls(primary: String): List<String> =
            linkedSetOf(
                primary.trim(),
                RAW_FEED,
                JSDELIVR_FEED,
            ).filter { it.isNotBlank() }

        fun parseManifest(json: String): AppUpdateManifest? {
            return try {
                val o = JSONObject(json)
                val code = o.optInt("versionCode", -1)
                val name = o.optString("versionName").trim()
                val apk = o.optString("apkUrl").trim()
                if (code < 1 || name.isEmpty() || apk.isEmpty()) return null
                AppUpdateManifest(
                    versionCode = code,
                    versionName = name,
                    apkUrl = apk,
                    changelog = o.optString("changelog").trim(),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
