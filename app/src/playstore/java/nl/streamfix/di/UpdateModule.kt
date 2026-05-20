package nl.streamfix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.data.repository.NoOpUpdateRepository
import nl.streamfix.domain.repository.UpdateRepository

/** Play Store-flavor: no-op-updater (geen self-update toegestaan). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(
        impl: NoOpUpdateRepository,
    ): UpdateRepository
}
