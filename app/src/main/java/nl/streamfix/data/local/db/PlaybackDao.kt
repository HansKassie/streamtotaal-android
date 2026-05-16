package nl.streamfix.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {

    @Query(
        "SELECT positionMs FROM playback_progress " +
            "WHERE accountId = :accountId AND mediaId = :mediaId",
    )
    suspend fun position(accountId: String, mediaId: String): Long?

    /** Volledige rij incl. metadata (start van kijken / resume vanuit historie). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackProgressEntity)

    /** Alleen positie bijwerken; metadata blijft staan. */
    @Query(
        "UPDATE playback_progress SET positionMs = :positionMs, " +
            "updatedAt = :updatedAt " +
            "WHERE accountId = :accountId AND mediaId = :mediaId",
    )
    suspend fun updatePosition(
        accountId: String,
        mediaId: String,
        positionMs: Long,
        updatedAt: Long,
    )

    @Query(
        "SELECT * FROM playback_progress WHERE accountId = :accountId " +
            "ORDER BY updatedAt DESC",
    )
    fun observeHistory(accountId: String): Flow<List<PlaybackProgressEntity>>
}
