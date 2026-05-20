package nl.streamfix.ui

import android.content.Context
import androidx.annotation.StringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import nl.streamfix.R
import nl.streamfix.domain.model.AppError

/**
 * Xtream exp_date is een Unix-timestamp (seconden). Maak er een leesbare
 * datum van in de toestel-tijdzone. Leeg/0 = onbeperkt; niet-numeriek
 * (sommige panels sturen al een datumtekst) wordt ongewijzigd getoond.
 */
fun formatXtreamExpiry(context: Context, raw: String?): String? {
    val value = raw?.trim()
    if (value.isNullOrEmpty()) return null
    val seconds = value.toLongOrNull() ?: return value
    if (seconds <= 0L) return context.getString(R.string.expiry_unlimited)
    return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        .format(Date(seconds * 1000L))
}

/** Gelokaliseerde, gebruiksvriendelijke foutmeldingen. */
@StringRes
fun AppError.uiMessageRes(): Int = when (this) {
    AppError.InvalidCredentials -> R.string.err_invalid_credentials
    AppError.SubscriptionExpired -> R.string.err_subscription_expired
    AppError.ServerUnreachable -> R.string.err_server_unreachable
    AppError.ProviderUnavailable -> R.string.err_provider_unavailable
    AppError.NetworkUnavailable -> R.string.err_network_unavailable
    AppError.NotAnXtreamServer -> R.string.err_not_xtream_server
    AppError.InvalidUrl -> R.string.err_invalid_url
    AppError.Unknown -> R.string.err_unknown
}

/** Resolveert de foutmelding in de huidige toestel-locale. */
fun AppError.uiMessage(context: Context): String =
    context.getString(uiMessageRes())
