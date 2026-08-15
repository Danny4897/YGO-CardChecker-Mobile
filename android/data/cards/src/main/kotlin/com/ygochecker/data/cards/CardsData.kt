package com.ygochecker.data.cards

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.CardRepository
import com.ygochecker.core.domain.OfflinePackRepository
import com.ygochecker.core.domain.PreferenceRepository
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.SearchFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val type: String,
    val race: String?,
    val attribute: String?,
    val attack: Int?,
    val defense: Int?,
    val level: Int?,
    val description: String,
    val year: Int? = null,
)

@Entity(tableName = "format_limits", primaryKeys = ["cardId", "format"])
data class FormatLimitEntity(
    val cardId: Int,
    val format: String,
    val maxCopies: Int,
)

@Entity(tableName = "effect_scripts")
data class EffectScriptEntity(
    @PrimaryKey val cardId: Int,
    val name: String,
    val rolesCsv: String,
    /** Comma-separated mechanic tags (gy_effect, ss_from_gy, …). */
    val tagsCsv: String = "",
)

@Entity(tableName = "card_relations", primaryKeys = ["sourceId", "targetId", "relation"])
data class CardRelationEntity(
    val sourceId: Int,
    val targetId: Int,
    val relation: String,
    val score: Double,
    val targetName: String,
)

/** Lightweight projection for synergy queries. */
data class CardIdName(
    val id: Int,
    val name: String,
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    fun search(query: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun searchOnce(query: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<CardEntity>

    /** Newest passcodes first among cards playable (maxCopies > 0) in [format]. */
    @Query(
        """
        SELECT c.* FROM cards c
        INNER JOIN format_limits f ON c.id = f.cardId
        WHERE f.format = :format AND f.maxCopies > 0
        ORDER BY c.id DESC
        LIMIT :limit
        """,
    )
    suspend fun playableByFormat(format: String, limit: Int): List<CardEntity>

    @Query(
        """
        SELECT c.* FROM cards c
        INNER JOIN format_limits f ON c.id = f.cardId
        WHERE f.format = :format AND f.maxCopies > 0
        ORDER BY RANDOM()
        LIMIT 1
        """,
    )
    suspend fun randomPlayable(format: String): CardEntity?

    @Query(
        """
        SELECT c.* FROM cards c
        INNER JOIN format_limits f ON c.id = f.cardId
        LEFT JOIN effect_scripts e ON c.id = e.cardId
        WHERE f.format = :format AND f.maxCopies > 0
          AND (
            :typeNeedle = ''
            OR (:typeNeedle = 'EXTRA' AND (
              c.type LIKE '%Fusion%' OR c.type LIKE '%Synchro%' OR c.type LIKE '%Xyz%'
              OR c.type LIKE '%XYZ%' OR c.type LIKE '%Link%'
            ))
            OR (:typeNeedle != 'EXTRA' AND c.type LIKE '%' || :typeNeedle || '%')
          )
          AND (:race = '' OR UPPER(c.race) = UPPER(:race))
          AND (:attribute = '' OR UPPER(c.attribute) = UPPER(:attribute))
          AND (:levelMin < 0 OR (c.level IS NOT NULL AND c.level >= :levelMin))
          AND (:levelMax < 0 OR (c.level IS NOT NULL AND c.level <= :levelMax))
          AND (:atkMin < 0 OR (c.attack IS NOT NULL AND c.attack >= :atkMin))
          AND (:atkMax < 0 OR (c.attack IS NOT NULL AND c.attack <= :atkMax))
          AND (:defMin < 0 OR (c.defense IS NOT NULL AND c.defense >= :defMin))
          AND (:defMax < 0 OR (c.defense IS NOT NULL AND c.defense <= :defMax))
          AND (:yearMin < 0 OR (c.year IS NOT NULL AND c.year >= :yearMin))
          AND (:yearMax < 0 OR (c.year IS NOT NULL AND c.year <= :yearMax))
          AND (:tagNeedle = '' OR (',' || e.tagsCsv || ',') LIKE '%,' || :tagNeedle || ',%')
          AND (:nameNeedle = '' OR c.name LIKE '%' || :nameNeedle || '%')
        ORDER BY c.id DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFiltered(
        format: String,
        nameNeedle: String,
        typeNeedle: String,
        race: String,
        attribute: String,
        levelMin: Int,
        levelMax: Int,
        atkMin: Int,
        atkMax: Int,
        defMin: Int,
        defMax: Int,
        yearMin: Int,
        yearMax: Int,
        tagNeedle: String,
        limit: Int,
    ): List<CardEntity>

    @Query("SELECT * FROM cards ORDER BY id DESC LIMIT :limit")
    suspend fun latestById(limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Int): CardEntity?

    @Query("SELECT * FROM cards WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByName(name: String): CardEntity?

    @Query("SELECT * FROM cards ORDER BY name")
    suspend fun all(): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Query("DELETE FROM format_limits")
    suspend fun clearFormatLimits()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormatLimits(rows: List<FormatLimitEntity>)

    @Query("SELECT COUNT(*) FROM format_limits")
    suspend fun formatLimitCount(): Int

    @Query("SELECT maxCopies FROM format_limits WHERE cardId = :cardId AND format = :format LIMIT 1")
    suspend fun formatLimit(cardId: Int, format: String): Int?

    @Query("SELECT * FROM format_limits")
    suspend fun allFormatLimits(): List<FormatLimitEntity>

    @Query("DELETE FROM effect_scripts")
    suspend fun clearEffectScripts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEffectScripts(rows: List<EffectScriptEntity>)

    @Query("SELECT COUNT(*) FROM effect_scripts")
    suspend fun effectScriptCount(): Int

    @Query("SELECT * FROM effect_scripts WHERE cardId = :id LIMIT 1")
    suspend fun effectScript(id: Int): EffectScriptEntity?

    @Query("SELECT * FROM effect_scripts WHERE cardId IN (:ids)")
    suspend fun effectScripts(ids: List<Int>): List<EffectScriptEntity>

    @Query("DELETE FROM card_relations")
    suspend fun clearCardRelations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardRelations(rows: List<CardRelationEntity>)

    @Query(
        """
        SELECT * FROM card_relations
        WHERE sourceId = :sourceId
        ORDER BY score DESC, targetName ASC
        LIMIT :limit
        """,
    )
    suspend fun relatedFor(sourceId: Int, limit: Int = 24): List<CardRelationEntity>

    @Query(
        """
        SELECT c.id AS id, c.name AS name FROM cards c
        INNER JOIN effect_scripts e ON c.id = e.cardId
        WHERE c.id != :excludeId
          AND (',' || e.tagsCsv || ',') LIKE '%,' || :tag || ',%'
        ORDER BY c.name ASC
        LIMIT :limit
        """,
    )
    suspend fun cardsWithEffectTag(tag: String, excludeId: Int, limit: Int): List<CardIdName>

    @Query(
        """
        SELECT id, name FROM cards
        WHERE id != :excludeId
          AND (name LIKE '%' || :needle || '%' OR description LIKE '%' || :needle || '%')
        ORDER BY
          CASE WHEN name LIKE '%' || :needle || '%' THEN 0 ELSE 1 END,
          name ASC
        LIMIT :limit
        """,
    )
    suspend fun cardsMatchingNeedle(needle: String, excludeId: Int, limit: Int): List<CardIdName>

    @Query(
        """
        SELECT id, name FROM cards
        WHERE id != :excludeId
          AND attribute = :attribute
          AND race = :race
        ORDER BY name ASC
        LIMIT :limit
        """,
    )
    suspend fun cardsWithAttributeRace(
        attribute: String,
        race: String,
        excludeId: Int,
        limit: Int,
    ): List<CardIdName>

    @Query(
        """
        SELECT id, name FROM cards
        WHERE id != :excludeId
          AND UPPER(IFNULL(race, '')) = UPPER(:race)
        ORDER BY name ASC
        LIMIT :limit
        """,
    )
    suspend fun cardsWithRace(race: String, excludeId: Int, limit: Int): List<CardIdName>

    @Query("SELECT COUNT(*) FROM card_relations")
    suspend fun cardRelationCount(): Int

    @Query("SELECT value FROM sync_meta WHERE key = :key LIMIT 1")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(row: SyncMetaEntity)
}

@Database(
    entities = [
        CardEntity::class,
        FormatLimitEntity::class,
        EffectScriptEntity::class,
        CardRelationEntity::class,
        SyncMetaEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cards(): CardDao
}

@Singleton
class RoomCardRepository @Inject constructor(
    private val dao: CardDao,
    private val api: YgoProDeckClient,
    private val preferences: PreferenceRepository,
    private val offlinePack: OfflinePackRepository,
) : CardRepository {
    override fun search(query: String): Flow<List<Card>> =
        search(query, GameFormat.TCG, SearchFilters(playableOnly = false))

    override fun search(
        query: String,
        format: GameFormat,
        filters: SearchFilters,
        limit: Int,
    ): Flow<List<Card>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() && (filters.playableOnly || filters.hasFacets)) {
            return flow {
                emit(runFilteredSearch(format, filters, nameNeedle = "", limit = limit))
            }.flowOn(Dispatchers.IO)
        }
        if (trimmed.isEmpty()) {
            return browsePlayable(format, limit)
        }
        // Name query: local (+ optional playable/facet filter), then enrich via API fname
        // so IT/EN typing works even when playableOnly is on.
        return flow {
            emit(runFilteredOrPlain(format, filters, trimmed, limit))
            try {
                val primary = preferences.language.first()
                var remote = api.searchByFname(trimmed, primary.id, limit = 50)
                if (remote.isEmpty() && primary == AppLanguage.ITALIAN) {
                    remote = api.searchByFname(trimmed, "en", limit = 50)
                }
                if (remote.isNotEmpty()) {
                    val existingIds = dao.getByIds(remote.map(Card::id)).map(CardEntity::id).toSet()
                    val toInsert = remote.filter { it.id !in existingIds }
                    if (toInsert.isNotEmpty()) dao.insertAll(toInsert.map(Card::toEntity))
                    emit(runFilteredOrPlain(format, filters, trimmed, limit))
                }
            } catch (_: IOException) {
            } catch (_: Exception) {
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun runFilteredOrPlain(
        format: GameFormat,
        filters: SearchFilters,
        nameNeedle: String,
        limit: Int,
    ): List<Card> {
        if (filters.playableOnly || filters.hasFacets) {
            return runFilteredSearch(format, filters, nameNeedle, limit)
        }
        return dao.searchOnce(nameNeedle).map(CardEntity::toModel).take(limit)
    }

    private suspend fun runFilteredSearch(
        format: GameFormat,
        filters: SearchFilters,
        nameNeedle: String,
        limit: Int,
    ): List<Card> =
        dao.searchFiltered(
            format = format.id,
            nameNeedle = nameNeedle,
            typeNeedle = filters.typeNeedle,
            race = filters.race,
            attribute = filters.attribute,
            levelMin = filters.levelMin ?: -1,
            levelMax = filters.levelMax ?: -1,
            atkMin = filters.atkMin ?: -1,
            atkMax = filters.atkMax ?: -1,
            defMin = filters.defMin ?: -1,
            defMax = filters.defMax ?: -1,
            yearMin = filters.yearMin ?: -1,
            yearMax = filters.yearMax ?: -1,
            tagNeedle = filters.effectTag,
            limit = limit,
        ).map(CardEntity::toModel)

    override fun browsePlayable(format: GameFormat, limit: Int): Flow<List<Card>> = flow {
        emit(dao.playableByFormat(format.id, limit).map(CardEntity::toModel))
    }.flowOn(Dispatchers.IO)

    override fun randomPlayable(format: GameFormat): Flow<Card?> = flow {
        emit(dao.randomPlayable(format.id)?.toModel())
    }.flowOn(Dispatchers.IO)

    override suspend fun get(id: Int) = dao.get(id)?.toModel()

    override suspend fun all() = dao.all().map(CardEntity::toModel)

    override suspend fun count() = dao.count()

    override suspend fun ensureCatalogReady() {
        // Import (YDKE/YDK) can race Application startup — wait for HAT pack first.
        offlinePack.ensureBundledKnowledge()
        if (dao.count() == 0) dao.insertAll(seed)
    }

    override suspend fun resolveByIds(ids: Collection<Int>): AppResult<List<Card>> {
        val unique = ids.filter { it > 0 }.distinct()
        if (unique.isEmpty()) return AppResult.Ok(emptyList())

        val local = dao.getByIds(unique).map(CardEntity::toModel).associateBy(Card::id)
        val missing = unique.filterNot(local::containsKey)
        if (missing.isEmpty()) return AppResult.Ok(unique.mapNotNull(local::get))

        // Fill gaps from YGOPRODeck when possible. Network failure must NOT look like
        // "you're offline" for YDKE/YDK import — report unresolved passcodes instead.
        val remote = try {
            api.fetchByIds(missing, language = null)
        } catch (_: Exception) {
            emptyList()
        }
        if (remote.isNotEmpty()) dao.insertAll(remote.map(Card::toEntity))
        val merged = local + remote.associateBy(Card::id)
        val stillMissing = unique.filterNot(merged::containsKey)
        return if (stillMissing.isNotEmpty()) {
            AppResult.Err(AppError("deck.import.ydke_card_not_found", stillMissing.sorted().joinToString()))
        } else {
            AppResult.Ok(unique.mapNotNull(merged::get))
        }
    }

    override suspend fun resolveByName(name: String): Card? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        dao.getByName(trimmed)?.toModel()?.let { return it }
        return try {
            val primary = preferences.language.first()
            val secondary = if (primary == AppLanguage.ITALIAN) AppLanguage.ENGLISH else AppLanguage.ITALIAN
            val remote = api.fetchByExactName(trimmed, primary.id)
                ?: api.fetchByExactName(trimmed, secondary.id)
            if (remote != null && dao.get(remote.id) == null) {
                dao.insertAll(listOf(remote.toEntity()))
            }
            remote
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun localized(id: Int, language: AppLanguage): Card? {
        if (id <= 0) return null
        val local = dao.get(id)?.toModel()
        return try {
            val langParam = if (language == AppLanguage.ITALIAN) "it" else null
            api.fetchByIds(listOf(id), language = langParam).firstOrNull() ?: local
        } catch (_: IOException) {
            local
        } catch (_: Exception) {
            local
        }
    }
}

internal fun CardEntity.toModel() =
    Card(id, name, type, race, attribute, attack, defense, level, description, year)

internal fun Card.toEntity() =
    CardEntity(id, name, type, race, attribute, attack, defense, level, description, year)

private val seed = listOf(
    CardEntity(89631139, "Blue-Eyes White Dragon", "Normal Monster", "Dragon", "LIGHT", 3000, 2500, 8, "This legendary dragon is a powerful engine of destruction.", 2002),
    CardEntity(46986414, "Dark Magician", "Normal Monster", "Spellcaster", "DARK", 2500, 2100, 7, "The ultimate wizard in terms of attack and defense.", 2002),
    CardEntity(55144522, "Pot of Greed", "Normal Spell", "Normal", null, null, null, null, "Draw 2 cards.", 2002),
    CardEntity(83764718, "Monster Reborn", "Normal Spell", "Normal", null, null, null, null, "Target 1 monster in either GY; Special Summon it.", 2002),
    CardEntity(44095762, "Sangan", "Effect Monster", "Fiend", "DARK", 1000, 600, 3, "Searches a low-ATK monster when sent to the GY.", 2002),
    CardEntity(23995346, "Elemental HERO Flame Wingman", "Fusion Monster", "Warrior", "WIND", 2100, 1200, 6, "Elemental HERO Avian + Elemental HERO Burstinatrix", 2004),
    CardEntity(53129443, "Bottomless Trap Hole", "Normal Trap", "Normal", null, null, null, null, "Banish Summoned monsters with 1500 or more ATK.", 2003),
    CardEntity(12580477, "Raigeki", "Normal Spell", "Normal", null, null, null, null, "Destroy all monsters your opponent controls.", 2002),
)
