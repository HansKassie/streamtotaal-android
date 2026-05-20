package nl.streamfix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.data.repository.UpdateRepositoryImpl
import nl.streamfix.domain.repository.UpdateRepository

/** Sideload-flavor: echte updater via R2 + DownloadManager. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(
        impl: UpdateRepositoryImpl,
    ): UpdateRepository
}
