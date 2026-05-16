package nl.streamfix.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteChannelEntity::class], version = 1, exportSchema = false)
abstract class StreamFixDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
