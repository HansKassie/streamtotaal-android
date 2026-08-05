package nl.streamfix.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite_channels WHERE accountId = :accountId ORDER BY name")
    fun observe(accountId: String): Flow<List<FavoriteChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteChannelEntity)

    @Query(
        "DELETE FROM favorite_channels WHERE accountId = :accountId " +
            "AND channelId = :channelId",
    )
    suspend fun remove(accountId: String, channelId: String)

    /** Favorieten die nog geen volwassen-classificatie hebben (oude rijen). */
    @Query(
        "SELECT * FROM favorite_channels WHERE accountId = :accountId " +
            "AND isAdult IS NULL",
    )
    suspend fun unclassified(accountId: String): List<FavoriteChannelEntity>

    @Query(
        "UPDATE favorite_channels SET categoryId = :categoryId, " +
            "isAdult = :isAdult WHERE accountId = :accountId " +
            "AND channelId = :channelId",
    )
    suspend fun classify(
        accountId: String,
        channelId: String,
        categoryId: String?,
        isAdult: Boolean,
    )
}
