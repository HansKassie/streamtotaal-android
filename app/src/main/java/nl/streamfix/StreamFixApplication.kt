package nl.streamfix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import io.sentry.SentryLevel

@HiltAndroidApp
class StreamFixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initCrashReporting()
    }

    private fun initCrashReporting() {
        // Vul SENTRY_DSN in via SETUP.md. Leeg = Sentry uitgeschakeld.
        if (SENTRY_DSN.isBlank()) return

        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "streamfix@${BuildConfig.VERSION_NAME}"
            // AVG: geen persoonsgegevens meesturen (briefing 6.1)
            options.isSendDefaultPii = false
            options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                event.user = null
                event
            }
            options.setDebug(BuildConfig.DEBUG)
            options.setDiagnosticLevel(SentryLevel.WARNING)
        }
    }

    companion object {
        // Sentry-project "android" (org syncapp-beheer). DSN is geen geheim:
        // hoort client-side in de app, dus mag in de broncode.
        private const val SENTRY_DSN =
            "https://f16704e9bf67c61e08330af478b44b2f" +
                "@o4511403691540480.ingest.de.sentry.io/4511403704057936"
    }
}
