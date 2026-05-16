package nl.streamfix.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackDao {

    @Query(
        "SELECT positionMs FROM playback_progress " +
            "WHERE accountId = :accountId AND mediaId = :mediaId",
    )
    suspend fun position(accountId: String, mediaId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: PlaybackProgressEntity)
}
