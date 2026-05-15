package nl.streamfix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import io.sentry.SentryLevel

@HiltAndroidApp
class StreamfixApplication : Application() {

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
        // TODO(SETUP.md): vervang door echte DSN uit het Sentry-dashboard
        private const val SENTRY_DSN = ""
    }
}
