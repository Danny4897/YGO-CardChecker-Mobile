package com.ygochecker.data.deck

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.SocialRepository
import com.ygochecker.core.model.DeckSection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.FriendshipStatus
import com.ygochecker.core.model.SocialChatMessage
import com.ygochecker.core.model.SocialDeckCard
import com.ygochecker.core.model.SocialDmMessage
import com.ygochecker.core.model.SocialPublicDeck
import com.ygochecker.core.model.SocialPublicDeckSummary
import com.ygochecker.core.model.SocialUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private val socialTokenKey = stringPreferencesKey("social_api_token")
private val socialDeviceKey = stringPreferencesKey("social_device_id")
private val socialApiUrlKey = stringPreferencesKey("social_api_url")

@Singleton
class HttpSocialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    @Named("socialApiBaseUrl") private val defaultBaseUrl: String,
) : SocialRepository {
    override fun observeApiUrl(): Flow<String> =
        context.appPrefsStore.data.map { prefs ->
            prefs[socialApiUrlKey]?.trim()?.takeIf { it.isNotEmpty() } ?: defaultBaseUrl.trim()
        }

    override suspend fun setApiUrl(url: String) {
        context.appPrefsStore.edit {
            val cleaned = url.trim().trimEnd('/')
            if (cleaned.isEmpty()) it.remove(socialApiUrlKey)
            else it[socialApiUrlKey] = cleaned
        }
    }

    private suspend fun resolveBase(): String =
        context.appPrefsStore.data.first()[socialApiUrlKey]?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultBaseUrl.trim()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun ensureSession(username: String, avatarCardId: Int?): AppResult<SocialUser> =
        withContext(Dispatchers.IO) {
            if (resolveBase().isBlank()) return@withContext AppResult.Err(AppError("social.not_configured"))
            val deviceId = deviceId()
            val body = JSONObject()
                .put("deviceId", deviceId)
                .put("username", username.ifBlank { "Duelist" })
                .apply {
                    if (avatarCardId != null) put("avatarCardId", avatarCardId)
                    else put("avatarCardId", JSONObject.NULL)
                }
            when (val r = request("POST", "/v1/register", body, auth = false)) {
                is AppResult.Err -> r
                is AppResult.Ok -> {
                    val root = r.value
                    val token = root.optString("token")
                    if (token.isBlank()) return@withContext AppResult.Err(AppError("social.bad_response"))
                    context.appPrefsStore.edit { it[socialTokenKey] = token }
                    parseUser(root.getJSONObject("user")).let { AppResult.Ok(it) }
                }
            }
        }

    override suspend fun refreshMe(): AppResult<SocialUser> = getJson("/v1/me") { parseUser(it.getJSONObject("user")) }

    override suspend fun updateMe(username: String, avatarCardId: Int?): AppResult<SocialUser> {
        val body = JSONObject()
            .put("username", username)
            .apply {
                if (avatarCardId == null) put("avatarCardId", JSONObject.NULL)
                else put("avatarCardId", avatarCardId)
            }
        return request("PATCH", "/v1/me", body).mapUser()
    }

    override suspend fun searchUsers(query: String): AppResult<List<SocialUser>> =
        getJson("/v1/users/search?q=${encode(query)}") { root ->
            root.getJSONArray("users").toUserList()
        }

    override suspend fun getUser(idOrCode: String): AppResult<SocialUser> =
        getJson("/v1/users/${encode(idOrCode)}") { parseUser(it.getJSONObject("user")) }

    override suspend fun requestFriend(toUserId: String): AppResult<FriendshipStatus> {
        val body = JSONObject().put("toUserId", toUserId)
        return request("POST", "/v1/friends/request", body).map { parseFriendship(it.optString("friendship")) }
    }

    override suspend fun acceptFriend(fromUserId: String): AppResult<FriendshipStatus> {
        val body = JSONObject().put("fromUserId", fromUserId)
        return request("POST", "/v1/friends/accept", body).map { parseFriendship(it.optString("friendship")) }
    }

    override suspend fun listFriends(): AppResult<Pair<List<SocialUser>, List<SocialUser>>> =
        getJson("/v1/friends") { root ->
            root.getJSONArray("friends").toUserList() to root.getJSONArray("incoming").toUserList()
        }

    override suspend fun publishDeck(deck: Decklist): AppResult<Unit> {
        val cards = JSONArray()
        deck.cards.forEach { dc ->
            cards.put(
                JSONObject()
                    .put("cardId", dc.card.id)
                    .put("quantity", dc.quantity)
                    .put("section", dc.section.name.lowercase())
                    .put("name", dc.card.name),
            )
        }
        val covers = JSONArray()
        deck.coverCardIds.forEach { covers.put(it) }
        val body = JSONObject()
            .put("name", deck.name)
            .put("coverCardIds", covers)
            .put("cards", cards)
        return request("PUT", "/v1/me/decks/${deck.id}", body).map { }
    }

    override suspend fun unpublishDeck(localDeckId: Long): AppResult<Unit> =
        request("DELETE", "/v1/me/decks/$localDeckId", null).map { }

    override suspend fun listPublicDecks(userId: String): AppResult<List<SocialPublicDeckSummary>> =
        getJson("/v1/users/${encode(userId)}/decks") { root ->
            val arr = root.getJSONArray("decks")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SocialPublicDeckSummary(
                            id = o.getString("id"),
                            ownerId = o.getString("ownerId"),
                            localDeckId = o.optLong("localDeckId"),
                            name = o.optString("name"),
                            coverCardIds = o.optJSONArray("coverCardIds").toIntList(),
                            updatedAt = o.optLong("updatedAt"),
                        ),
                    )
                }
            }
        }

    override suspend fun getPublicDeck(deckId: String): AppResult<SocialPublicDeck> =
        getJson("/v1/decks/${encode(deckId)}") { root ->
            val o = root.getJSONObject("deck")
            val cardsArr = o.optJSONArray("cards") ?: JSONArray()
            val cards = buildList {
                for (i in 0 until cardsArr.length()) {
                    val c = cardsArr.getJSONObject(i)
                    add(
                        SocialDeckCard(
                            cardId = c.optInt("cardId"),
                            quantity = c.optInt("quantity", 1),
                            section = c.optString("section", DeckSection.MAIN.name.lowercase()),
                            name = c.optString("name"),
                        ),
                    )
                }
            }
            SocialPublicDeck(
                id = o.getString("id"),
                ownerId = o.getString("ownerId"),
                ownerUsername = o.optString("ownerUsername"),
                localDeckId = o.optLong("localDeckId"),
                name = o.optString("name"),
                coverCardIds = o.optJSONArray("coverCardIds").toIntList(),
                cards = cards,
                updatedAt = o.optLong("updatedAt"),
            )
        }

    override suspend fun listDeckMessages(deckId: String, lang: String?): AppResult<List<SocialChatMessage>> {
        val q = if (lang.isNullOrBlank()) "" else "?lang=${encode(lang)}"
        return getJson("/v1/decks/${encode(deckId)}/messages$q") { root ->
            root.getJSONArray("messages").toChatList()
        }
    }

    override suspend fun postDeckMessage(
        deckId: String,
        body: String,
        lang: String,
    ): AppResult<SocialChatMessage> {
        val payload = JSONObject().put("body", body).put("lang", lang)
        return request("POST", "/v1/decks/${encode(deckId)}/messages", payload).map {
            parseChat(it.getJSONObject("message"))
        }
    }

    override suspend fun listDm(peerUserId: String): AppResult<List<SocialDmMessage>> =
        getJson("/v1/dm/${encode(peerUserId)}/messages") { root ->
            val arr = root.getJSONArray("messages")
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SocialDmMessage(
                            id = o.getLong("id"),
                            fromUserId = o.getString("fromUserId"),
                            toUserId = o.getString("toUserId"),
                            body = o.optString("body"),
                            createdAt = o.optLong("createdAt"),
                        ),
                    )
                }
            }
        }

    override suspend fun postDm(peerUserId: String, body: String): AppResult<SocialDmMessage> {
        val payload = JSONObject().put("body", body)
        return request("POST", "/v1/dm/${encode(peerUserId)}/messages", payload).map {
            val o = it.getJSONObject("message")
            SocialDmMessage(
                id = o.getLong("id"),
                fromUserId = o.getString("fromUserId"),
                toUserId = o.getString("toUserId"),
                body = o.optString("body"),
                createdAt = o.optLong("createdAt"),
            )
        }
    }

    private suspend fun deviceId(): String {
        val prefs = context.appPrefsStore.data.first()
        prefs[socialDeviceKey]?.takeIf { it.length >= 8 }?.let { return it }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val id = androidId?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: UUID.randomUUID().toString()
        context.appPrefsStore.edit { it[socialDeviceKey] = id }
        return id
    }

    private suspend fun token(): String? =
        context.appPrefsStore.data.first()[socialTokenKey]

    private suspend fun <T> getJson(path: String, parse: (JSONObject) -> T): AppResult<T> =
        request("GET", path, null).map(parse)

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
        auth: Boolean = true,
    ): AppResult<JSONObject> = withContext(Dispatchers.IO) {
        val base = resolveBase()
        if (base.isBlank()) return@withContext AppResult.Err(AppError("social.not_configured"))
        val url = base.trimEnd('/') + path
        val builder = Request.Builder().url(url)
        if (auth) {
            val t = token() ?: return@withContext AppResult.Err(AppError("social.unauthorized"))
            builder.header("Authorization", "Bearer $t")
        }
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> {
                val raw = (body ?: JSONObject()).toString()
                builder.method(method, raw.toRequestBody(jsonMedia))
            }
        }
        try {
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse {
                    return@withContext AppResult.Err(AppError("social.bad_response"))
                }
                if (!resp.isSuccessful) {
                    val key = json.optString("errorKey").ifBlank { "social.http_${resp.code}" }
                    return@withContext AppResult.Err(AppError(key))
                }
                AppResult.Ok(json)
            }
        } catch (_: Exception) {
            AppResult.Err(AppError("social.network"))
        }
    }

    private fun AppResult<JSONObject>.mapUser(): AppResult<SocialUser> = map { parseUser(it.getJSONObject("user")) }

    private inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Ok -> AppResult.Ok(transform(value))
        is AppResult.Err -> this
    }

    private fun parseUser(o: JSONObject): SocialUser {
        val avatar = when {
            o.isNull("avatarCardId") -> null
            else -> o.optInt("avatarCardId").takeIf { o.has("avatarCardId") }
        }
        return SocialUser(
            id = o.getString("id"),
            username = o.optString("username"),
            friendCode = o.optString("friendCode"),
            avatarCardId = avatar,
            friendship = parseFriendship(o.optString("friendship")),
        )
    }

    private fun parseFriendship(raw: String?): FriendshipStatus = when (raw) {
        "friends" -> FriendshipStatus.FRIENDS
        "pending_out" -> FriendshipStatus.PENDING_OUT
        "pending_in" -> FriendshipStatus.PENDING_IN
        else -> FriendshipStatus.NONE
    }

    private fun JSONArray.toUserList(): List<SocialUser> = buildList {
        for (i in 0 until length()) add(parseUser(getJSONObject(i)))
    }

    private fun JSONArray.toChatList(): List<SocialChatMessage> = buildList {
        for (i in 0 until length()) add(parseChat(getJSONObject(i)))
    }

    private fun parseChat(o: JSONObject): SocialChatMessage = SocialChatMessage(
        id = o.getLong("id"),
        userId = o.getString("userId"),
        username = o.optString("username"),
        body = o.optString("body"),
        lang = o.optString("lang", "en"),
        createdAt = o.optLong("createdAt"),
    )

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(optInt(i))
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
