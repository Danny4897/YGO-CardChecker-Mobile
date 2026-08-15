package com.ygochecker.data.deck

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.model.CardCollection
import com.ygochecker.core.model.FriendEntry
import com.ygochecker.core.model.LinkedAccountProvider
import com.ygochecker.core.model.ReplayEntry
import com.ygochecker.core.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "collection_cards", primaryKeys = ["collectionId", "cardId"])
data class CollectionCardEntity(
    val collectionId: Long,
    val cardId: Int,
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val note: String = "",
)

@Entity(tableName = "replays")
data class ReplayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_cards")
    fun observeCollectionCards(): Flow<List<CollectionCardEntity>>

    @Insert
    suspend fun insertCollection(row: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id=:id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM collection_cards WHERE collectionId=:id")
    suspend fun clearCollectionCards(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCollectionCard(row: CollectionCardEntity)

    @Query("DELETE FROM collection_cards WHERE collectionId=:collectionId AND cardId=:cardId")
    suspend fun removeCollectionCard(collectionId: Long, cardId: Int)

    @Query("SELECT * FROM friends ORDER BY displayName COLLATE NOCASE")
    fun observeFriends(): Flow<List<FriendEntity>>

    @Insert
    suspend fun insertFriend(row: FriendEntity): Long

    @Query("DELETE FROM friends WHERE id=:id")
    suspend fun deleteFriend(id: Long)

    @Query("SELECT * FROM replays ORDER BY createdAt DESC")
    fun observeReplays(): Flow<List<ReplayEntity>>

    @Insert
    suspend fun insertReplay(row: ReplayEntity): Long

    @Query("DELETE FROM replays WHERE id=:id")
    suspend fun deleteReplay(id: Long)
}

private val profileUsernameKey = stringPreferencesKey("profile_username")
private val profileAvatarKey = intPreferencesKey("profile_avatar_card")

/**
 * Local profile + encrypted linked account IDs.
 * Stores opaque provider IDs only — never passwords or OAuth refresh tokens in this MVP.
 */
@Singleton
class RoomProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProfileDao,
) : ProfileRepository {
    private val linkedPrefs: SharedPreferences by lazy { encryptedPrefs(context) }
    private val linkTick = MutableStateFlow(0)

    override fun observeProfile(): Flow<UserProfile> =
        combine(context.appPrefsStore.data, linkTick) { prefs, _ ->
            UserProfile(
                username = prefs[profileUsernameKey].orEmpty(),
                avatarCardId = prefs[profileAvatarKey],
                linkedAccounts = readLinkedAccounts(),
            )
        }

    override suspend fun setUsername(username: String) {
        context.appPrefsStore.edit { it[profileUsernameKey] = username.trim().take(32) }
    }

    override suspend fun setAvatarCardId(cardId: Int?) {
        context.appPrefsStore.edit { prefs ->
            if (cardId == null || cardId <= 0) prefs.remove(profileAvatarKey)
            else prefs[profileAvatarKey] = cardId
        }
    }

    override suspend fun linkAccount(provider: LinkedAccountProvider, externalId: String): AppResult<Unit> {
        val cleaned = sanitizeExternalId(provider, externalId) ?: return AppResult.Err(AppError("profile.link_invalid"))
        linkedPrefs.edit().putString(provider.name, cleaned).apply()
        linkTick.value = linkTick.value + 1
        return AppResult.Ok(Unit)
    }

    override suspend fun unlinkAccount(provider: LinkedAccountProvider) {
        linkedPrefs.edit().remove(provider.name).apply()
        linkTick.value = linkTick.value + 1
    }

    override fun observeFriends(): Flow<List<FriendEntry>> =
        dao.observeFriends().map { rows ->
            rows.map { FriendEntry(it.id, it.displayName, it.note) }
        }

    override suspend fun addFriend(displayName: String, note: String): AppResult<Unit> {
        val name = displayName.trim().take(40)
        if (name.length < 2) return AppResult.Err(AppError("profile.friend_invalid"))
        dao.insertFriend(FriendEntity(displayName = name, note = note.trim().take(80)))
        return AppResult.Ok(Unit)
    }

    override suspend fun removeFriend(id: Long) {
        dao.deleteFriend(id)
    }

    override fun observeCollections(): Flow<List<CardCollection>> =
        combine(dao.observeCollections(), dao.observeCollectionCards()) { collections, cards ->
            val byCollection = cards.groupBy { it.collectionId }
            collections.map { col ->
                CardCollection(
                    id = col.id,
                    name = col.name,
                    cardIds = byCollection[col.id].orEmpty().map { it.cardId },
                )
            }
        }

    override suspend fun createCollection(name: String): Long {
        val cleaned = name.trim().take(40).ifBlank { "Collection" }
        return dao.insertCollection(CollectionEntity(name = cleaned))
    }

    override suspend fun deleteCollection(id: Long) {
        dao.clearCollectionCards(id)
        dao.deleteCollection(id)
    }

    override suspend fun addCardToCollection(collectionId: Long, cardId: Int) {
        if (collectionId <= 0 || cardId <= 0) return
        dao.putCollectionCard(CollectionCardEntity(collectionId, cardId))
    }

    override suspend fun removeCardFromCollection(collectionId: Long, cardId: Int) {
        dao.removeCollectionCard(collectionId, cardId)
    }

    override fun observeReplays(): Flow<List<ReplayEntry>> =
        dao.observeReplays().map { rows ->
            rows.map { ReplayEntry(it.id, it.title, it.note, it.createdAt) }
        }

    override suspend fun addReplay(title: String, note: String): AppResult<Unit> {
        val cleaned = title.trim().take(80)
        if (cleaned.length < 2) return AppResult.Err(AppError("profile.replay_invalid"))
        dao.insertReplay(ReplayEntity(title = cleaned, note = note.trim().take(400)))
        return AppResult.Ok(Unit)
    }

    override suspend fun removeReplay(id: Long) {
        dao.deleteReplay(id)
    }

    private fun readLinkedAccounts(): Map<LinkedAccountProvider, String> =
        LinkedAccountProvider.entries.mapNotNull { provider ->
            linkedPrefs.getString(provider.name, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { provider to it }
        }.toMap()

    companion object {
        private fun encryptedPrefs(context: Context): SharedPreferences {
            val master = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "ygochecker_linked_accounts",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        /** Reject password-like input; accept opaque IDs / handles only. */
        fun sanitizeExternalId(provider: LinkedAccountProvider, raw: String): String? {
            val value = raw.trim()
            if (value.length !in 3..64) return null
            if (value.contains(' ') || value.contains('\n')) return null
            // Heuristic: reject password-like digit soup — except numeric Discord/Konami IDs.
            if (value.count { it.isDigit() } > value.length * 0.6 &&
                !value.any { it == '#' || it == '@' || it == '-' || it == '_' || it == '.' }
            ) {
                if (provider != LinkedAccountProvider.KONAMI && provider != LinkedAccountProvider.DISCORD) {
                    return null
                }
            }
            return when (provider) {
                LinkedAccountProvider.DISCORD -> value.removePrefix("@").takeIf {
                    it.matches(Regex("^[A-Za-z0-9._#-]{2,80}$"))
                }
                LinkedAccountProvider.GOOGLE -> value.takeIf {
                    it.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) ||
                        it.matches(Regex("^[A-Za-z0-9_-]{6,64}$"))
                }
                LinkedAccountProvider.KONAMI -> value.takeIf {
                    it.matches(Regex("^[A-Za-z0-9_-]{3,32}$"))
                }
            }
        }
    }
}
