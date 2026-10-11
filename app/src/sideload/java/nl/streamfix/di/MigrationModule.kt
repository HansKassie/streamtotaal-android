package nl.streamfix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.data.repository.NetworkNameMigration
import nl.streamfix.domain.repository.AccountMigrations

/** Sideload-flavor: opgeslagen providernamen omzetten naar netwerkletters. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MigrationModule {

    @Binds
    @Singleton
    abstract fun bindAccountMigrations(
        impl: NetworkNameMigration,
    ): AccountMigrations
}
