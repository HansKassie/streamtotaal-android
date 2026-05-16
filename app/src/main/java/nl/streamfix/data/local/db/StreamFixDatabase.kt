package nl.streamfix.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoriteChannelEntity::class, PlaybackProgressEntity::class],
    version = 3,
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE playback_progress ADD COLUMN title " +
                "TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL("ALTER TABLE playback_progress ADD COLUMN posterUrl TEXT")
        db.execSQL(
            "ALTER TABLE playback_progress ADD COLUMN type " +
                "TEXT NOT NULL DEFAULT 'vod'",
        )
        db.execSQL(
            "ALTER TABLE playback_progress ADD COLUMN contentId " +
                "TEXT NOT NULL DEFAULT ''",
        )
        db.execSQL(
            "ALTER TABLE playback_progress ADD COLUMN extension " +
                "TEXT NOT NULL DEFAULT 'mp4'",
        )
    }
}
