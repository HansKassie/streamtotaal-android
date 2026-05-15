package nl.streamfix.domain.model

/**
 * Getypte fouten zodat de UI gebruiksvriendelijke meldingen kan tonen
 * (briefing-acceptatiecriterium: foutmeldingen zijn gebruikersvriendelijk).
 */
enum class AppError {
    InvalidCredentials,
    SubscriptionExpired,
    ServerUnreachable,
    NetworkUnavailable,
    NotAnXtreamServer,
    InvalidM3u,
    Unknown,
}
