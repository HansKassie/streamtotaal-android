package nl.streamfix.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import nl.streamfix.domain.util.EpgRequestGate

/**
 * Een eigen [EpgRequestGate] per ViewModel.
 *
 * De poort is sessietoestand van één lijst: welke zenders al gevraagd zijn
 * en welke onlangs faalden. Gedeeld maken zou de ene lijst laten beslissen
 * over de andere. Geinjecteerd en niet ter plekke aangemaakt, zodat een test
 * er een met een kortere wachttijd in kan zetten; zonder dat is "faalt, en
 * krijgt later een nieuwe kans" in een ViewModel-test niet te bewijzen.
 */
@Module
@InstallIn(ViewModelComponent::class)
object EpgModule {

    @Provides
    fun provideEpgRequestGate(): EpgRequestGate = EpgRequestGate()
}
