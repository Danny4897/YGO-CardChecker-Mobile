package com.ygochecker.android.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.ygochecker.android.BuildConfig
import com.ygochecker.core.domain.AccountLinkEvent
import com.ygochecker.core.domain.AccountLinker
import com.ygochecker.core.domain.AuthClientConfig
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.model.LinkedAccountProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android account linking:
 * - Discord / Google: Custom Tabs OAuth (prefers installed apps) → deep link callback
 * - Konami: no public third-party OAuth → [AccountLinkEvent.NeedsManualConfirm]
 *
 * Multi-device: re-auth with the same provider reconstitutes the same opaque subject ID.
 * Profile content stays on-device until a sync backend exists.
 */
@Singleton
class AndroidAccountLinker @Inject constructor(
    private val profile: ProfileRepository,
    private val http: OkHttpClient,
) : AccountLinker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<AccountLinkEvent>(extraBufferCapacity = 4)
    override val events: SharedFlow<AccountLinkEvent> = _events.asSharedFlow()

    private val config = AuthClientConfig(
        discordClientId = BuildConfig.DISCORD_CLIENT_ID.trim(),
        discordClientSecret = BuildConfig.DISCORD_CLIENT_SECRET.trim(),
        googleClientId = BuildConfig.GOOGLE_CLIENT_ID.trim(),
    )

    @Volatile private var pkceVerifier: String? = null
    @Volatile private var pendingProvider: LinkedAccountProvider? = null

    override fun startLink(activity: Activity, provider: LinkedAccountProvider) {
        when (provider) {
            LinkedAccountProvider.KONAMI -> {
                pendingProvider = provider
                openUrl(activity, KONAMI_ID_PORTAL)
                scope.launch { _events.emit(AccountLinkEvent.NeedsManualConfirm(provider)) }
            }
            LinkedAccountProvider.DISCORD -> startDiscord(activity)
            LinkedAccountProvider.GOOGLE -> startGoogle(activity)
        }
    }

    override fun handleRedirect(uri: Uri) {
        val provider = when (uri.lastPathSegment?.lowercase()) {
            "discord" -> LinkedAccountProvider.DISCORD
            "google" -> LinkedAccountProvider.GOOGLE
            else -> pendingProvider ?: return
        }
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            scope.launch {
                _events.emit(AccountLinkEvent.Failed(provider, "profile.link_oauth_denied"))
            }
            return
        }
        val code = uri.getQueryParameter("code") ?: run {
            scope.launch {
                _events.emit(AccountLinkEvent.NeedsManualConfirm(provider))
            }
            return
        }
        scope.launch { exchangeCode(provider, code) }
    }

    private fun startDiscord(activity: Activity) {
        pendingProvider = LinkedAccountProvider.DISCORD
        if (!config.discordConfigured) {
            // Prefer Discord mobile; user confirms ID after return.
            openDiscordAppOrWeb(activity)
            scope.launch {
                _events.emit(AccountLinkEvent.NeedsManualConfirm(LinkedAccountProvider.DISCORD))
            }
            return
        }
        val verifier = newPkceVerifier()
        pkceVerifier = verifier
        val challenge = pkceChallenge(verifier)
        val uri = Uri.parse("https://discord.com/api/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", config.discordClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", DISCORD_REDIRECT)
            .appendQueryParameter("scope", "identify")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "consent")
            .build()
        openUrl(activity, uri.toString())
    }

    private fun startGoogle(activity: Activity) {
        pendingProvider = LinkedAccountProvider.GOOGLE
        if (!config.googleConfigured) {
            openUrl(activity, "https://accounts.google.com/")
            scope.launch {
                _events.emit(AccountLinkEvent.NeedsManualConfirm(LinkedAccountProvider.GOOGLE))
            }
            return
        }
        val verifier = newPkceVerifier()
        pkceVerifier = verifier
        val challenge = pkceChallenge(verifier)
        val uri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", config.googleClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", GOOGLE_REDIRECT)
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "online")
            .appendQueryParameter("prompt", "select_account")
            .build()
        openUrl(activity, uri.toString())
    }

    private suspend fun exchangeCode(provider: LinkedAccountProvider, code: String) {
        try {
            val subject = when (provider) {
                LinkedAccountProvider.DISCORD -> exchangeDiscord(code)
                LinkedAccountProvider.GOOGLE -> exchangeGoogle(code)
                LinkedAccountProvider.KONAMI -> null
            }
            if (subject.isNullOrBlank()) {
                _events.emit(AccountLinkEvent.Failed(provider, "profile.link_oauth_failed"))
                return
            }
            when (val result = profile.linkAccount(provider, subject)) {
                is com.ygochecker.core.common.AppResult.Ok ->
                    _events.emit(AccountLinkEvent.Linked(provider, subject))
                is com.ygochecker.core.common.AppResult.Err ->
                    _events.emit(AccountLinkEvent.Failed(provider, result.error.errorKey))
            }
        } catch (_: Exception) {
            _events.emit(AccountLinkEvent.Failed(provider, "profile.link_oauth_failed"))
        } finally {
            pkceVerifier = null
            pendingProvider = null
        }
    }

    private fun exchangeDiscord(code: String): String? {
        if (config.discordClientSecret.isBlank()) {
            // Public clients need a token proxy; fall back to manual confirm.
            return null
        }
        val body = FormBody.Builder()
            .add("client_id", config.discordClientId)
            .add("client_secret", config.discordClientSecret)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", DISCORD_REDIRECT)
            .apply { pkceVerifier?.let { add("code_verifier", it) } }
            .build()
        val tokenReq = Request.Builder()
            .url("https://discord.com/api/oauth2/token")
            .post(body)
            .build()
        val tokenJson = http.newCall(tokenReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty())
        }
        val access = tokenJson.optString("access_token").ifBlank { return null }
        val meReq = Request.Builder()
            .url("https://discord.com/api/users/@me")
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        val me = http.newCall(meReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty())
        }
        val id = me.optString("id")
        return id.takeIf { it.isNotBlank() }
    }

    private fun exchangeGoogle(code: String): String? {
        val verifier = pkceVerifier ?: return null
        val body = FormBody.Builder()
            .add("client_id", config.googleClientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", GOOGLE_REDIRECT)
            .add("code_verifier", verifier)
            .build()
        val tokenReq = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()
        val tokenJson = http.newCall(tokenReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty())
        }
        val access = tokenJson.optString("access_token").ifBlank { return null }
        val meReq = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        val me = http.newCall(meReq).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty())
        }
        return me.optString("email").ifBlank { me.optString("sub") }.ifBlank { null }
    }

    private fun openDiscordAppOrWeb(activity: Activity) {
        val discord = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/app")).apply {
            setPackage("com.discord")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(discord)
        } catch (_: Exception) {
            openUrl(activity, "https://discord.com/login")
        }
    }

    private fun openUrl(activity: Activity, url: String) {
        CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(url))
    }

    companion object {
        const val DISCORD_REDIRECT = "ygochecker://oauth/discord"
        const val GOOGLE_REDIRECT = "ygochecker://oauth/google"
        private const val KONAMI_ID_PORTAL = "https://account.konami.net/"

        private fun newPkceVerifier(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        private fun pkceChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
