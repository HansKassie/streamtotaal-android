package nl.streamfix.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import nl.streamfix.domain.model.AppError

/**
 * Zet een [AppError] om in de tekst die de kijker te zien krijgt.
 *
 * Bestaat zodat een ViewModel geen Context hoeft te dragen voor het enige
 * dat het ervan nodig had: een foutmelding in de juiste taal. Daarmee zijn
 * die ViewModels op de JVM te testen, zonder Robolectric en zonder mock.
 *
 * De keuze WELKE tekst bij welke fout hoort blijft in [uiMessageRes], want
 * die is al zuiver en wordt ook rechtstreeks vanuit Compose gebruikt. Deze
 * interface gaat alleen over het opzoeken van de tekst zelf.
 */
interface ErrorTexts {
    fun of(error: AppError): String
}

@Singleton
class AndroidErrorTexts @Inject constructor(
    @ApplicationContext private val context: Context,
) : ErrorTexts {
    override fun of(error: AppError): String =
        context.getString(error.uiMessageRes())
}
