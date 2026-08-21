package com.ygochecker.data.social

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/** Row DTOs mirror the Postgres schema exactly (`supabase/migrations` on the `ygochecker` project). */

@Serializable
internal data class ProfileRow(
    val id: String,
    val username: String,
    @SerialName("friend_code") val friendCode: String,
    @SerialName("avatar_card_id") val avatarCardId: Int? = null,
)

@Serializable
internal data class FriendshipRow(
    val id: Long,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    val status: String,
    @SerialName("requested_by") val requestedBy: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
internal data class FriendshipInsertRow(
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    val status: String,
    @SerialName("requested_by") val requestedBy: String,
)

@Serializable
internal data class SocialDeckCardRow(
    @SerialName("cardId") val cardId: Int,
    val quantity: Int,
    val section: String,
    val name: String = "",
)

@Serializable
internal data class PublicDeckSummaryRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("local_deck_id") val localDeckId: Long,
    val name: String,
    @SerialName("cover_card_ids") val coverCardIds: List<Int> = emptyList(),
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
internal data class PublicDeckRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("local_deck_id") val localDeckId: Long,
    val name: String,
    @SerialName("cover_card_ids") val coverCardIds: List<Int> = emptyList(),
    val cards: List<SocialDeckCardRow> = emptyList(),
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
internal data class PublicDeckUpsertRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("local_deck_id") val localDeckId: Long,
    val name: String,
    @SerialName("cover_card_ids") val coverCardIds: List<Int>,
    val cards: List<SocialDeckCardRow>,
)

@Serializable
internal data class DeckMessageRow(
    val id: Long,
    @SerialName("deck_id") val deckId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    val lang: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class DeckMessageInsertRow(
    @SerialName("deck_id") val deckId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    val lang: String,
)

@Serializable
internal data class DmMessageRow(
    val id: Long,
    @SerialName("thread_key") val threadKey: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class DmMessageInsertRow(
    @SerialName("thread_key") val threadKey: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    val body: String,
)

@Serializable
internal data class UserAlertRow(
    val id: Long,
    @SerialName("profile_id") val profileId: String = "",
    val kind: String,
    val payload: Map<String, String> = emptyMap(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
)

@Serializable
internal data class CollectionRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
internal data class CollectionCardRow(
    @SerialName("collection_id") val collectionId: String,
    @SerialName("card_id") val cardId: Int,
    val qty: Int = 1,
)

/** Postgres `timestamptz` comes back from PostgREST as an ISO-8601 offset string. */
internal fun parseTimestampMillis(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
