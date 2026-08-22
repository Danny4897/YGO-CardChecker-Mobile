package com.ygochecker.data.deck

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.ygochecker.core.domain.TournamentRepository
import com.ygochecker.core.model.DeckNotes
import com.ygochecker.core.model.MatchResult
import com.ygochecker.core.model.TournamentMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "deck_notes")
data class DeckNotesEntity(
    @PrimaryKey val deckId: Long,
    val strengths: String = "",
    val weaknesses: String = "",
    val strategy: String = "",
    val updatedAt: Long = 0,
)

@Entity(tableName = "tournament_matches")
data class TournamentMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val roundLabel: String,
    val opponent: String,
    val opponentDeckId: Long? = null,
    val result: String,
    /** Comma-separated [MatchResult] names in play order, e.g. "WIN,LOSS,WIN". */
    val gamesCsv: String,
    val sideNotes: String,
    val notes: String,
    val createdAt: Long,
)

@Dao
interface TournamentDao {
    @Query("SELECT * FROM deck_notes WHERE deckId = :deckId")
    fun observeNotes(deckId: Long): Flow<DeckNotesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNotes(notes: DeckNotesEntity)

    @Query("SELECT * FROM tournament_matches WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun observeMatches(deckId: Long): Flow<List<TournamentMatchEntity>>

    @Insert
    suspend fun insertMatch(match: TournamentMatchEntity): Long

    @Delete
    suspend fun deleteMatch(match: TournamentMatchEntity)

    @Query("DELETE FROM tournament_matches WHERE id = :id")
    suspend fun deleteMatchById(id: Long)
}

/**
 * Separate database from [DeckDatabase]: that one holds real user data (decks, friends,
 * collections) under `fallbackToDestructiveMigration()`, so bumping its version would wipe
 * it. Tournament prep data is new and empty for every existing install, so an isolated
 * destructive-fallback database here is safe without hand-written Room migrations.
 */
@Database(
    entities = [DeckNotesEntity::class, TournamentMatchEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class TournamentDatabase : RoomDatabase() {
    abstract fun tournament(): TournamentDao
}

@Singleton
class RoomTournamentRepository @Inject constructor(
    private val dao: TournamentDao,
) : TournamentRepository {
    override fun observeNotes(deckId: Long): Flow<DeckNotes?> =
        dao.observeNotes(deckId).map { it?.toModel() }

    override suspend fun saveNotes(notes: DeckNotes) {
        dao.putNotes(
            DeckNotesEntity(
                deckId = notes.deckId,
                strengths = notes.strengths,
                weaknesses = notes.weaknesses,
                strategy = notes.strategy,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun observeMatches(deckId: Long): Flow<List<TournamentMatch>> =
        dao.observeMatches(deckId).map { rows -> rows.map { it.toModel() } }

    override suspend fun addMatch(match: TournamentMatch): Long =
        dao.insertMatch(
            TournamentMatchEntity(
                deckId = match.deckId,
                roundLabel = match.roundLabel,
                opponent = match.opponent,
                opponentDeckId = match.opponentDeckId,
                result = match.result.name,
                gamesCsv = match.games.joinToString(",") { it.name },
                sideNotes = match.sideNotes,
                notes = match.notes,
                createdAt = System.currentTimeMillis(),
            ),
        )

    override suspend fun deleteMatch(id: Long) = dao.deleteMatchById(id)
}

private fun DeckNotesEntity.toModel() = DeckNotes(deckId, strengths, weaknesses, strategy, updatedAt)

private fun TournamentMatchEntity.toModel() = TournamentMatch(
    id = id,
    deckId = deckId,
    roundLabel = roundLabel,
    opponent = opponent,
    opponentDeckId = opponentDeckId,
    result = result.toMatchResult(),
    games = gamesCsv.split(',').filter(String::isNotBlank).map { it.toMatchResult() },
    sideNotes = sideNotes,
    notes = notes,
    createdAt = createdAt,
)

private fun String.toMatchResult(): MatchResult = runCatching { MatchResult.valueOf(this) }.getOrDefault(MatchResult.DRAW)
