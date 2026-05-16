package nl.streamfix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.data.repository.AuthRepositoryImpl
import nl.streamfix.data.repository.LiveRepositoryImpl
import nl.streamfix.data.repository.EpgRepositoryImpl
import nl.streamfix.data.repository.PlaybackRepositoryImpl
import nl.streamfix.data.repository.ProviderRepositoryImpl
import nl.streamfix.data.repository.SeriesRepositoryImpl
import nl.streamfix.data.repository.VodRepositoryImpl
import nl.streamfix.domain.repository.AuthRepository
import nl.streamfix.domain.repository.EpgRepository
import nl.streamfix.domain.repository.LiveRepository
import nl.streamfix.domain.repository.PlaybackRepository
import nl.streamfix.domain.repository.ProviderRepository
import nl.streamfix.domain.repository.SeriesRepository
import nl.streamfix.domain.repository.VodRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindLiveRepository(impl: LiveRepositoryImpl): LiveRepository

    @Binds
    @Singleton
    abstract fun bindVodRepository(impl: VodRepositoryImpl): VodRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        impl: PlaybackRepositoryImpl,
    ): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindSeriesRepository(
        impl: SeriesRepositoryImpl,
    ): SeriesRepository

    @Binds
    @Singleton
    abstract fun bindEpgRepository(impl: EpgRepositoryImpl): EpgRepository

    @Binds
    @Singleton
    abstract fun bindProviderRepository(
        impl: ProviderRepositoryImpl,
    ): ProviderRepository
}
