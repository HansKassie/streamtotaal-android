package nl.streamfix.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.data.local.db.FavoriteDao
import nl.streamfix.data.local.db.MIGRATION_1_2
import nl.streamfix.data.local.db.MIGRATION_2_3
import nl.streamfix.data.local.db.PlaybackDao
import nl.streamfix.data.local.db.StreamFixDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StreamFixDatabase =
        Room.databaseBuilder(
            context,
            StreamFixDatabase::class.java,
            "streamfix.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    @Provides
    fun provideFavoriteDao(db: StreamFixDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaybackDao(db: StreamFixDatabase): PlaybackDao = db.playbackDao()
}
