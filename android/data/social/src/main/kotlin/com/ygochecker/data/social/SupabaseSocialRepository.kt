package com.ygochecker.data.social

import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.SocialRepository
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.FriendshipStatus
import com.ygochecker.core.model.SocialAlert
import com.ygochecker.core.model.SocialAlertKind
import com.ygochecker.core.model.SocialAuthState
import com.ygochecker.core.model.SocialChatMessage
import com.ygochecker.core.model.SocialCollection
import com.ygochecker.core.model.SocialDeckCard
import com.ygochecker.core.model.SocialDmMessage
import com.ygochecker.core.model.SocialPublicDeck
import com.ygochecker.core.model.SocialPublicDeckSummary
import com.ygochecker.core.model.SocialUser
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Social backend on Supabase (Postgres + Auth + Realtime), replacing the old
 * SQLite micro-server. Every table has row-level security — see the `ygochecker`
 * Supabase project migrations for the exact policies this implementation relies on.
 *
 * Every suspend function returns [AppResult] (never throws) per this codebase's
 * "no throw for expected outcomes" convention. Flows never throw either.
 */
@Singleton
class SupabaseSocialRepository @Inject constructor(
    private val client: SupabaseClient,
    @Named("supabaseUrl") private val supabaseUrl: String,
) : SocialRepository {

    private val profileColumns = Columns.list("id", "username", "friend_code", "avatar_card_id")
    private val deckSummaryColumns = Columns.list("id", "owner_id", "local_deck_id", "name", "cover_card_ids", "updated_at")
    private val deckColumns = Columns.list("id", "owner_id", "local_deck_id", "name", "cover_card_ids", "cards", "updated_at")

    override fun observeApiUrl(): Flow<String> = flowOf(supabaseUrl)

    override fun observeAuthState(): Flow<SocialAuthState> =
        client.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated ->
                    if (status.session.user?.email.isNullOrBlank()) SocialAuthState.ANONYMOUS else SocialAuthState.EMAIL_LINKED
                else -> SocialAuthState.SIGNED_OUT
            }
        }.catch { emit(SocialAuthState.SIGNED_OUT) }

    private fun currentUid(): String? = client.auth.currentUserOrNull()?.id

    private suspend fun requireUid(): String {
        currentUid()?.let { return it }
        client.auth.signInAnonymously()
        return currentUid() ?: throw IllegalStateException("social.auth_failed")
    }

    override suspend fun ensureSession(username: String, avatarCardId: Int?): AppResult<SocialUser> = safe {
        val uid = requireUid()
        val row = client.postgrest.from("profiles").update(
            update = {
                if (username.isNotBlank()) set("username", username.trim().take(32))
                set("avatar_card_id", avatarCardId)
            },
        ) {
            select(profileColumns)
            filter { eq("id", uid) }
        }.decodeSingle<ProfileRow>()
        row.toSocialUser()
    }

    override suspend fun refreshMe(): AppResult<SocialUser> = safe {
        val uid = requireUid()
        client.postgrest.from("profiles").select(profileColumns) {
            filter { eq("id", uid) }
        }.decodeSingle<ProfileRow>().toSocialUser()
    }

    override suspend fun updateMe(username: String, avatarCardId: Int?): AppResult<SocialUser> = safe {
        val uid = requireUid()
        client.postgrest.from("profiles").update(
            update = {
                set("username", username.trim().take(32))
                set("avatar_card_id", avatarCardId)
            },
        ) {
            select(profileColumns)
            filter { eq("id", uid) }
        }.decodeSingle<ProfileRow>().toSocialUser()
    }

    override suspend fun searchUsers(query: String): AppResult<List<SocialUser>> = safe {
        val uid = requireUid()
        val safeQuery = query.trim().take(40).replace("%", "").replace("_", "")
        if (safeQuery.length < 2) return@safe emptyList()
        val rows = client.postgrest.from("profiles").select(profileColumns) {
            filter {
                or {
                    ilike("username", "%$safeQuery%")
                    ilike("friend_code", "%${safeQuery.uppercase()}%")
                }
                neq("id", uid)
            }
            limit(30)
        }.decodeList<ProfileRow>()
        rows.map { it.toSocialUser() }
    }

    override suspend fun getUser(idOrCode: String): AppResult<SocialUser> = safe {
        val q = idOrCode.trim()
        val looksLikeUuid = q.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
        val row = client.postgrest.from("profiles").select(profileColumns) {
            filter {
                if (looksLikeUuid) eq("id", q) else eq("friend_code", q.uppercase())
            }
        }.decodeSingleOrNull<ProfileRow>() ?: throw AppErrorException("social.user_not_found")
        row.toSocialUser()
    }

    override suspend fun requestFriend(toUserId: String): AppResult<FriendshipStatus> = safe {
        val me = requireUid()
        if (toUserId == me) throw AppErrorException("social.friend_self")
        val (a, b) = orderedPair(me, toUserId)
        val existing = client.postgrest.from("friendships").select {
            filter { eq("user_a", a); eq("user_b", b) }
        }.decodeSingleOrNull<FriendshipRow>()
        when {
            existing == null -> {
                client.postgrest.from("friendships").insert(
                    FriendshipInsertRow(userA = a, userB = b, status = "pending", requestedBy = me),
                )
                FriendshipStatus.PENDING_OUT
            }
            existing.status == "accepted" -> FriendshipStatus.FRIENDS
            existing.requestedBy == me -> FriendshipStatus.PENDING_OUT
            else -> {
                client.postgrest.from("friendships").update(
                    update = { set("status", "accepted") },
                ) {
                    filter { eq("id", existing.id) }
                }
                FriendshipStatus.FRIENDS
            }
        }
    }

    override suspend fun acceptFriend(fromUserId: String): AppResult<FriendshipStatus> = safe {
        val me = requireUid()
        val (a, b) = orderedPair(me, fromUserId)
        val existing = client.postgrest.from("friendships").select {
            filter { eq("user_a", a); eq("user_b", b) }
        }.decodeSingleOrNull<FriendshipRow>()
        if (existing == null || existing.status != "pending" || existing.requestedBy == me) {
            throw AppErrorException("social.friend_no_pending")
        }
        client.postgrest.from("friendships").update(
            update = { set("status", "accepted") },
        ) {
            filter { eq("id", existing.id) }
        }
        FriendshipStatus.FRIENDS
    }

    override suspend fun listFriends(): AppResult<Pair<List<SocialUser>, List<SocialUser>>> = safe {
        val me = requireUid()
        val rows = client.postgrest.from("friendships").select {
            filter { or { eq("user_a", me); eq("user_b", me) } }
        }.decodeList<FriendshipRow>()
        val accepted = rows.filter { it.status == "accepted" }
        val pendingIn = rows.filter { it.status == "pending" && it.requestedBy != me }
        val peerIds = (accepted.map { it.peerOf(me) } + pendingIn.map { it.peerOf(me) }).distinct()
        val peers = if (peerIds.isEmpty()) {
            emptyList()
        } else {
            client.postgrest.from("profiles").select(profileColumns) {
                filter { isIn("id", peerIds) }
            }.decodeList<ProfileRow>()
        }
        val byId = peers.associateBy { it.id }
        val friends = accepted.mapNotNull { byId[it.peerOf(me)] }.map { it.toSocialUser(FriendshipStatus.FRIENDS) }
        val incoming = pendingIn.mapNotNull { byId[it.peerOf(me)] }.map { it.toSocialUser(FriendshipStatus.PENDING_IN) }
        friends to incoming
    }

    override suspend fun publishDeck(deck: Decklist): AppResult<String> = safe {
        val me = requireUid()
        val id = "$me--${deck.id}"
        val cards = deck.cards.map {
            SocialDeckCardRow(cardId = it.card.id, quantity = it.quantity, section = it.section.name.lowercase(), name = it.card.name)
        }
        client.postgrest.from("public_decks").upsert(
            PublicDeckUpsertRow(
                id = id,
                ownerId = me,
                localDeckId = deck.id,
                name = deck.name,
                coverCardIds = deck.coverCardIds,
                cards = cards,
            ),
        ) {
            onConflict = "owner_id,local_deck_id"
        }
        id
    }

    override suspend fun unpublishDeck(localDeckId: Long): AppResult<Unit> = safe {
        val me = requireUid()
        client.postgrest.from("public_decks").delete {
            filter { eq("owner_id", me); eq("local_deck_id", localDeckId) }
        }
        Unit
    }

    override suspend fun listPublicDecks(userId: String): AppResult<List<SocialPublicDeckSummary>> = safe {
        val rows = client.postgrest.from("public_decks").select(deckSummaryColumns) {
            filter { eq("owner_id", userId) }
            order("updated_at", order = Order.DESCENDING)
        }.decodeList<PublicDeckSummaryRow>()
        val ownerName = usernameFor(userId)
        rows.map { it.toSummary(ownerName) }
    }

    override suspend fun listAllPublicDecks(limit: Int): AppResult<List<SocialPublicDeckSummary>> = safe {
        val rows = client.postgrest.from("public_decks").select(deckSummaryColumns) {
            order("updated_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<PublicDeckSummaryRow>()
        val owners = rows.map { it.ownerId }.distinct()
        val names = usernamesFor(owners)
        rows.map { it.toSummary(names[it.ownerId].orEmpty()) }
    }

    override suspend fun getPublicDeck(deckId: String): AppResult<SocialPublicDeck> = safe {
        val row = client.postgrest.from("public_decks").select(deckColumns) {
            filter { eq("id", deckId) }
        }.decodeSingleOrNull<PublicDeckRow>() ?: throw AppErrorException("social.deck_not_found")
        SocialPublicDeck(
            id = row.id,
            ownerId = row.ownerId,
            ownerUsername = usernameFor(row.ownerId),
            localDeckId = row.localDeckId,
            name = row.name,
            coverCardIds = row.coverCardIds,
            cards = row.cards.map {
                SocialDeckCard(cardId = it.cardId, quantity = it.quantity, section = it.section, name = it.name)
            },
            updatedAt = parseTimestampMillis(row.updatedAt),
        )
    }

    override suspend fun listDeckMessages(deckId: String, lang: String?): AppResult<List<SocialChatMessage>> = safe {
        val rows = client.postgrest.from("deck_messages").select {
            filter {
                eq("deck_id", deckId)
                if (!lang.isNullOrBlank()) eq("lang", lang)
            }
            order("created_at", order = Order.ASCENDING)
            limit(200)
        }.decodeList<DeckMessageRow>()
        val names = usernamesFor(rows.map { it.userId }.distinct())
        rows.map { it.toChatMessage(names[it.userId].orEmpty()) }
    }

    override suspend fun postDeckMessage(deckId: String, body: String, lang: String): AppResult<SocialChatMessage> = safe {
        val me = requireUid()
        val text = body.trim().take(500)
        if (text.isEmpty()) throw AppErrorException("social.message_empty")
        val row = client.postgrest.from("deck_messages").insert(
            DeckMessageInsertRow(deckId = deckId, userId = me, body = text, lang = lang.ifBlank { "en" }),
        ) { select() }.decodeSingle<DeckMessageRow>()
        row.toChatMessage(usernameFor(me))
    }

    override suspend fun listDm(peerUserId: String): AppResult<List<SocialDmMessage>> = safe {
        val me = requireUid()
        val key = threadKey(me, peerUserId)
        client.postgrest.from("dm_messages").select {
            filter { eq("thread_key", key) }
            order("created_at", order = Order.ASCENDING)
            limit(200)
        }.decodeList<DmMessageRow>().map { it.toDm() }
    }

    override suspend fun postDm(peerUserId: String, body: String): AppResult<SocialDmMessage> = safe {
        val me = requireUid()
        val text = body.trim().take(500)
        if (text.isEmpty()) throw AppErrorException("social.message_empty")
        val row = client.postgrest.from("dm_messages").insert(
            DmMessageInsertRow(threadKey = threadKey(me, peerUserId), fromUserId = me, toUserId = peerUserId, body = text),
        ) { select() }.decodeSingle<DmMessageRow>()
        row.toDm()
    }

    override suspend fun sendMagicLink(email: String): AppResult<Unit> = safe {
        client.auth.signInWith(OTP, redirectUrl = MAGIC_LINK_REDIRECT) { this.email = email.trim() }
        Unit
    }

    @OptIn(SupabaseExperimental::class)
    override fun observeAlerts(): Flow<List<SocialAlert>> {
        val uid = currentUid() ?: return flowOf(emptyList())
        return client.postgrest.from("user_alerts").selectAsFlow(
            primaryKey = UserAlertRow::id,
            filter = FilterOperation("profile_id", FilterOperator.EQ, uid),
        ).map { rows ->
            rows.map { it.toAlert() }.sortedByDescending { it.createdAt }
        }.catch { emit(emptyList()) }
    }

    override suspend fun markAlertsRead(ids: List<Long>) {
        if (ids.isEmpty()) return
        runCatching {
            client.postgrest.from("user_alerts").update(
                update = { set("read_at", java.time.OffsetDateTime.now().toString()) },
            ) {
                filter { isIn("id", ids) }
            }
        }
    }

    override fun observeFriendCollections(peerUserId: String): Flow<List<SocialCollection>> = flow {
        val collections = client.postgrest.from("collections").select {
            filter { eq("owner_id", peerUserId) }
        }.decodeList<CollectionRow>()
        val ids = collections.map { it.id }
        val cards = if (ids.isEmpty()) {
            emptyList()
        } else {
            client.postgrest.from("collection_cards").select {
                filter { isIn("collection_id", ids) }
            }.decodeList<CollectionCardRow>()
        }
        val byCollection = cards.groupBy { it.collectionId }
        emit(
            collections.map { col ->
                SocialCollection(id = col.id, name = col.name, cardIds = byCollection[col.id].orEmpty().map { it.cardId })
            },
        )
    }.catch { emit(emptyList()) }

    // --- helpers ---

    private suspend fun usernameFor(userId: String): String =
        client.postgrest.from("profiles").select(Columns.list("username")) {
            filter { eq("id", userId) }
        }.decodeSingleOrNull<ProfileRow>()?.username.orEmpty()

    private suspend fun usernamesFor(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return client.postgrest.from("profiles").select(Columns.list("id", "username")) {
            filter { isIn("id", userIds) }
        }.decodeList<ProfileRow>().associate { it.id to it.username }
    }

    private fun ProfileRow.toSocialUser(status: FriendshipStatus = FriendshipStatus.NONE) = SocialUser(
        id = id,
        username = username,
        friendCode = friendCode,
        avatarCardId = avatarCardId,
        friendship = status,
    )

    private fun PublicDeckSummaryRow.toSummary(ownerUsername: String) = SocialPublicDeckSummary(
        id = id,
        ownerId = ownerId,
        ownerUsername = ownerUsername,
        localDeckId = localDeckId,
        name = name,
        coverCardIds = coverCardIds,
        updatedAt = parseTimestampMillis(updatedAt),
    )

    private fun DeckMessageRow.toChatMessage(username: String) = SocialChatMessage(
        id = id,
        userId = userId,
        username = username,
        body = body,
        lang = lang,
        createdAt = parseTimestampMillis(createdAt),
    )

    private fun DmMessageRow.toDm() = SocialDmMessage(
        id = id,
        fromUserId = fromUserId,
        toUserId = toUserId,
        body = body,
        createdAt = parseTimestampMillis(createdAt),
    )

    private fun UserAlertRow.toAlert() = SocialAlert(
        id = id,
        kind = when (kind) {
            "friend_request" -> SocialAlertKind.FRIEND_REQUEST
            "dm" -> SocialAlertKind.DM
            "deck_message" -> SocialAlertKind.DECK_MESSAGE
            else -> SocialAlertKind.BANLIST_CHANGE
        },
        payload = payload,
        createdAt = parseTimestampMillis(createdAt),
        read = readAt != null,
    )

    private fun FriendshipRow.peerOf(me: String): String = if (userA == me) userB else userA

    private fun orderedPair(a: String, b: String): Pair<String, String> = if (a < b) a to b else b to a

    private fun threadKey(a: String, b: String): String {
        val (x, y) = orderedPair(a, b)
        return "$x:$y"
    }

    private suspend inline fun <T> safe(crossinline block: suspend () -> T): AppResult<T> = try {
        AppResult.Ok(block())
    } catch (e: AppErrorException) {
        AppResult.Err(AppError(e.errorKey))
    } catch (e: Exception) {
        AppResult.Err(AppError(mapException(e)))
    }

    private fun mapException(e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            message.contains("JWT", ignoreCase = true) || message.contains("unauthorized", ignoreCase = true) -> "social.unauthorized"
            message.contains("network", ignoreCase = true) || message.contains("timeout", ignoreCase = true) -> "social.network"
            message.contains("duplicate", ignoreCase = true) || message.contains("unique", ignoreCase = true) -> "social.duplicate"
            else -> "social.auth_failed"
        }
    }
}

/** Internal control-flow exception mapped to a stable [AppError.errorKey] by [SupabaseSocialRepository.safe]. */
private class AppErrorException(val errorKey: String) : Exception(errorKey)

/** Matches the `ygochecker://oauth/magiclink` branch in `MainActivity.handleOAuthIntent`. */
private const val MAGIC_LINK_REDIRECT = "ygochecker://oauth/magiclink"
