package nl.streamfix.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpgDao {

    @Query(
        "SELECT * FROM epg_cache " +
            "WHERE accountId = :accountId AND streamId = :streamId",
    )
    suspend fun get(accountId: String, streamId: String): EpgCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EpgCacheEntity)
}
