package nl.streamfix.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteChannelEntity::class, PlaybackProgressEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class StreamFixDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackDao(): PlaybackDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS playback_progress (" +
                "accountId TEXT NOT NULL, " +
                "mediaId TEXT NOT NULL, " +
                "positionMs INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, " +
                "PRIMARY KEY(accountId, mediaId))",
        )
    }
}
