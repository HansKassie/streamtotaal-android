package nl.streamfix.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteMediaDao {

    @Query(
        "SELECT * FROM favorite_media WHERE accountId = :accountId " +
            "AND mediaType = :mediaType ORDER BY name",
    )
    fun observe(
        accountId: String,
        mediaType: String,
    ): Flow<List<FavoriteMediaEntity>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorite_media " +
            "WHERE accountId = :accountId AND mediaType = :mediaType " +
            "AND mediaId = :mediaId)",
    )
    fun observeIsFavorite(
        accountId: String,
        mediaType: String,
        mediaId: String,
    ): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteMediaEntity)

    @Query(
        "DELETE FROM favorite_media WHERE accountId = :accountId " +
            "AND mediaType = :mediaType AND mediaId = :mediaId",
    )
    suspend fun remove(accountId: String, mediaType: String, mediaId: String)
}
