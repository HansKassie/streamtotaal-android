package nl.streamfix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import nl.streamfix.data.local.AdultVisibility
import nl.streamfix.data.local.AppSettingsStore
import nl.streamfix.ui.AndroidErrorTexts
import nl.streamfix.ui.ErrorTexts

/**
 * Bindingen die een ViewModel losmaken van Android.
 *
 * Beide gaan over hetzelfde: een ViewModel vroeg een Context of een
 * versleutelde instellingenstore voor één klein ding, en was daardoor niet
 * op de JVM te testen. De echte implementaties zijn hier gebonden, een test
 * zet er zijn eigen voor in de plaats.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UiTextModule {

    @Binds
    @Singleton
    abstract fun bindErrorTexts(impl: AndroidErrorTexts): ErrorTexts

    @Binds
    @Singleton
    abstract fun bindAdultVisibility(impl: AppSettingsStore): AdultVisibility
}
