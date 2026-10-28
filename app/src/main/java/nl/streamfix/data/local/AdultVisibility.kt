package nl.streamfix.data.local

import kotlinx.coroutines.flow.StateFlow

/**
 * Alleen de volwassen-zichtbaarheid uit [AppSettingsStore].
 *
 * Bestaat om dezelfde reden als ErrorTexts: het zenderoverzicht heeft van
 * die store niets anders nodig dan deze ene stroom, terwijl de store zelf
 * op versleutelde SharedPreferences draait en daarmee een Context vraagt.
 * Via deze interface is dat ViewModel op de JVM te testen met een
 * MutableStateFlow van drie regels.
 */
interface AdultVisibility {
    val adultState: StateFlow<AdultState>
}
