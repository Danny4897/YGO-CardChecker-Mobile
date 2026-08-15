package com.ygochecker.android.update

import android.content.Context
import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.AppUpdateManifest
import com.ygochecker.core.domain.AppUpdateRepository
import com.ygochecker.android.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    override val feedUrl: String = BuildConfig.UPDATE_FEED_URL.trim()

    override suspend fun fetchManifest(): AppResult<AppUpdateManifest> = withContext(Dispatchers.IO) {
        val url = feedUrl
        if (url.isBlank()) return@withContext AppResult.Err(AppError("update.disabled"))
        try {
            val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Err(AppError("update.network"))
                }
                val body = response.body?.string().orEmpty()
                val parsed = parseManifest(body)
                    ?: return@withContext AppResult.Err(AppError("update.invalid"))
                AppResult.Ok(parsed)
            }
        } catch (_: IOException) {
            AppResult.Err(AppError("update.network"))
        } catch (_: Exception) {
            AppResult.Err(AppError("update.invalid"))
        }
    }

    override suspend fun skippedVersionCode(): Int =
        context.appPrefsStore.data.first()[skippedKey] ?: 0

    override suspend fun setSkippedVersionCode(versionCode: Int) {
        context.appPrefsStore.edit { it[skippedKey] = versionCode }
    }

    companion object {
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
