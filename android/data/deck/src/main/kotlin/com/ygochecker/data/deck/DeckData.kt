package com.ygochecker.data.deck

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.*
import com.ygochecker.core.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "deck_flow_links", primaryKeys = ["deckId", "flowId"])
data class DeckFlowLinkEntity(
    val deckId: Long,
    val flowId: String,
)

@Dao
interface DeckFlowLinkDao {
    @Query("SELECT flowId FROM deck_flow_links WHERE deckId = :deckId ORDER BY flowId")
    fun observeFlowIds(deckId: Long): Flow<List<String>>

    @Query("SELECT flowId FROM deck_flow_links WHERE deckId = :deckId ORDER BY flowId")
    suspend fun flowIds(deckId: Long): List<String>

    @Query("DELETE FROM deck_flow_links WHERE deckId = :deckId")
    suspend fun clearDeck(deckId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DeckFlowLinkEntity>)
}

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long,
    val coverCardId1: Int? = null,
    val coverCardId2: Int? = null,
    val isPublic: Boolean = false,
    val isPuzzleOpponent: Boolean = false,
)
@Entity(tableName = "deck_cards", primaryKeys = ["deckId", "cardId", "section"])
data class DeckCardEntity(
    val deckId: Long, val cardId: Int, val name: String, val type: String, val quantity: Int, val section: String,
)
@Dao interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY updatedAt DESC") fun observeDecks(): Flow<List<DeckEntity>>
    @Query("SELECT * FROM decks WHERE id=:id") fun observeDeck(id: Long): Flow<DeckEntity?>
    @Query("SELECT * FROM deck_cards WHERE deckId=:id ORDER BY name") fun observeCards(id: Long): Flow<List<DeckCardEntity>>
    @Query("SELECT * FROM deck_cards") fun observeAllCards(): Flow<List<DeckCardEntity>>
    @Insert suspend fun insert(deck: DeckEntity): Long
    @Query("UPDATE decks SET name=:name, updatedAt=:now WHERE id=:id") suspend fun rename(id: Long, name: String, now: Long)
    @Query("UPDATE decks SET updatedAt=:now WHERE id=:id") suspend fun touch(id: Long, now: Long)
    @Query("UPDATE decks SET coverCardId1=:c1, coverCardId2=:c2, updatedAt=:now WHERE id=:id")
    suspend fun setCovers(id: Long, c1: Int?, c2: Int?, now: Long)
    @Query("UPDATE decks SET isPublic=:isPublic, updatedAt=:now WHERE id=:id")
    suspend fun setPublic(id: Long, isPublic: Boolean, now: Long)
    @Query("UPDATE decks SET isPuzzleOpponent=:flag, updatedAt=:now WHERE id=:id")
    suspend fun setPuzzleOpponent(id: Long, flag: Boolean, now: Long)
    @Query("DELETE FROM decks WHERE id=:id") suspend fun delete(id: Long)
    @Query("DELETE FROM deck_cards WHERE deckId=:deckId AND cardId=:cardId AND section=:section")
    suspend fun removeCard(deckId: Long, cardId: Int, section: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCard(card: DeckCardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCards(cards: List<DeckCardEntity>)

    @Transaction suspend fun importDeck(name: String, now: Long, cards: List<DeckCardEntity>): Long {
        val deckId = insert(DeckEntity(name = name, updatedAt = now))
        putCards(cards.map { it.copy(deckId = deckId) })
        return deckId
    }
}
@Database(
    entities = [
        DeckEntity::class,
        DeckCardEntity::class,
        DeckFlowLinkEntity::class,
        CollectionEntity::class,
        CollectionCardEntity::class,
        FriendEntity::class,
        ReplayEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class DeckDatabase : RoomDatabase() {
    abstract fun decks(): DeckDao
    abstract fun profile(): ProfileDao
    abstract fun flowLinks(): DeckFlowLinkDao
}

@Singleton
class RoomDeckFlowLinkRepository @Inject constructor(
    private val dao: DeckFlowLinkDao,
) : DeckFlowLinkRepository {
    override suspend fun replaceLinks(deckId: Long, flowIds: List<String>) {
        dao.clearDeck(deckId)
        if (flowIds.isEmpty()) return
        dao.insertAll(flowIds.distinct().map { DeckFlowLinkEntity(deckId, it) })
    }

    override suspend fun flowIdsForDeck(deckId: Long): List<String> = dao.flowIds(deckId)

    override suspend fun clearDeck(deckId: Long) = dao.clearDeck(deckId)

    override fun observeFlowIds(deckId: Long): Flow<List<String>> = dao.observeFlowIds(deckId)
}

@Singleton class RoomDeckRepository @Inject constructor(
    private val dao: DeckDao,
    private val cards: CardRepository,
) : DeckRepository {
    override fun observeDecks() = combine(dao.observeDecks(), dao.observeAllCards()) { decks, cards ->
        val byDeck = cards.groupBy(DeckCardEntity::deckId)
        decks.map { deck ->
            Decklist(
                id = deck.id,
                name = deck.name,
                updatedAt = deck.updatedAt,
                cards = sortDeckCards(byDeck[deck.id].orEmpty().map(DeckCardEntity::toModel)),
                coverCardIds = listOfNotNull(deck.coverCardId1, deck.coverCardId2),
                isPublic = deck.isPublic,
                isPuzzleOpponent = deck.isPuzzleOpponent,
            )
        }
    }
    override fun observeDeck(id: Long) = combine(dao.observeDeck(id), dao.observeCards(id)) { deck, items ->
        deck?.let {
            Decklist(
                it.id,
                it.name,
                it.updatedAt,
                sortDeckCards(items.map(DeckCardEntity::toModel)),
                listOfNotNull(it.coverCardId1, it.coverCardId2),
                it.isPublic,
                it.isPuzzleOpponent,
            )
        }
    }
    override suspend fun create(name: String) = dao.insert(DeckEntity(name = name, updatedAt = System.currentTimeMillis()))
    override suspend fun rename(id: Long, name: String) = dao.rename(id, name, System.currentTimeMillis())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun setCard(id: Long, card: Card, quantity: Int, section: DeckSection) {
        if (quantity == 0) dao.removeCard(id, card.id, section.name)
        else dao.putCard(DeckCardEntity(id, card.id, card.name, card.type, quantity, section.name))
        dao.touch(id, System.currentTimeMillis())
    }
    override suspend fun setCoverCards(id: Long, coverCardIds: List<Int>) {
        val unique = coverCardIds.distinct().take(2)
        dao.setCovers(id, unique.getOrNull(0), unique.getOrNull(1), System.currentTimeMillis())
    }
    override suspend fun setPublic(id: Long, isPublic: Boolean) {
        dao.setPublic(id, isPublic, System.currentTimeMillis())
    }
    override suspend fun setPuzzleOpponent(id: Long, isPuzzleOpponent: Boolean) {
        dao.setPuzzleOpponent(id, isPuzzleOpponent, System.currentTimeMillis())
    }
    override suspend fun persistImported(name: String, cards: List<DeckCard>): AppResult<Long> {
        val normalized = normalizeImportedCards(cards)
        if (normalized is AppResult.Err) return normalized
        return try {
            val rows = (normalized as AppResult.Ok).value.map {
                DeckCardEntity(0, it.card.id, it.card.name, it.card.type, it.quantity, it.section.name)
            }
            AppResult.Ok(dao.importDeck(name, System.currentTimeMillis(), rows))
        } catch (_: Exception) {
            AppResult.Err(com.ygochecker.core.common.AppError("deck.storage_failed"))
        }
    }
    override suspend fun importText(text: String): AppResult<List<DeckCard>> {
        cards.ensureCatalogReady()
        return DeckTextCodec.import(text) { name -> cards.resolveByName(name) }
    }
    override fun exportText(cards: List<DeckCard>) = DeckTextCodec.export(cards)
    override suspend fun importYdke(uri: String): AppResult<List<DeckCard>> {
        cards.ensureCatalogReady()
        val decoded = YdkeCodec.import(uri)
        if (decoded is AppResult.Err) return decoded
        val passcodes = (decoded as AppResult.Ok).value
        val resolvedCatalog = cards.resolveByIds(passcodes.map { it.card.id })
        return when (resolvedCatalog) {
            is AppResult.Err -> resolvedCatalog
            is AppResult.Ok -> YdkeCodec.resolve(passcodes, resolvedCatalog.value)
        }
    }
    override fun exportYdke(cards: List<DeckCard>) = YdkeCodec.export(cards)

    override suspend fun importYdk(text: String): AppResult<List<DeckCard>> {
        cards.ensureCatalogReady()
        val decoded = YdkCodec.import(text)
        if (decoded is AppResult.Err) return decoded
        val passcodes = (decoded as AppResult.Ok).value
        val resolvedCatalog = cards.resolveByIds(passcodes.map { it.card.id })
        return when (resolvedCatalog) {
            is AppResult.Err -> resolvedCatalog
            is AppResult.Ok -> YdkeCodec.resolve(passcodes, resolvedCatalog.value)
        }
    }

    override fun exportYdk(cards: List<DeckCard>) = YdkCodec.export(cards)
}
private fun DeckCardEntity.toModel() = DeckCard(Card(cardId, name, type), quantity, DeckSection.valueOf(section))

val Context.appPrefsStore by preferencesDataStore("settings")
@Singleton class DataStorePreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceRepository, MdproAssetSettings {
    private val formatKey = stringPreferencesKey("format")
    private val languageKey = stringPreferencesKey("language")
    private val mdproRootKey = stringPreferencesKey("mdpro_root")
    override val format = context.appPrefsStore.data.map { p -> GameFormat.entries.firstOrNull { it.id == p[formatKey] } ?: GameFormat.TCG }
    override val language = context.appPrefsStore.data.map { p -> AppLanguage.entries.firstOrNull { it.id == p[languageKey] } ?: AppLanguage.ENGLISH }
    override fun rootPath(): Flow<String?> = context.appPrefsStore.data.map { p -> p[mdproRootKey] }
    override suspend fun setFormat(value: GameFormat) { context.appPrefsStore.edit { it[formatKey] = value.id } }
    override suspend fun setLanguage(value: AppLanguage) {
        context.appPrefsStore.edit { it[languageKey] = value.id }
        // App-wide resource locale — fixes leftover IT strings when UI language is EN.
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(value.id),
        )
    }
    override suspend fun setRootPath(path: String?) {
        context.appPrefsStore.edit {
            if (path.isNullOrBlank()) it.remove(mdproRootKey) else it[mdproRootKey] = path.trim()
        }
    }
}
