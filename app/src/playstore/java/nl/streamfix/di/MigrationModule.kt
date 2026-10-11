package nl.streamfix.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.domain.repository.AccountMigrations

/** Play Store-flavor: geen preset-netwerken, dus niets te hernoemen. */
@Module
@InstallIn(SingletonComponent::class)
object MigrationModule {

    @Provides
    @Singleton
    fun provideAccountMigrations(): AccountMigrations =
        object : AccountMigrations {
            override suspend fun run() = Unit
        }
}
