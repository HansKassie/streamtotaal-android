package nl.streamfix.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Cast-configuratie. We gebruiken de standaard media-ontvanger van Google,
 * dus geen eigen receiver-app nodig.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            )
            .build()

    override fun getAdditionalSessionProviders(
        context: Context,
    ): MutableList<SessionProvider>? = null
}
