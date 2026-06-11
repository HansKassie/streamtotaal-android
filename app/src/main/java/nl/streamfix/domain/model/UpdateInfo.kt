package nl.streamfix.domain.model

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val mandatory: Boolean,
    /** SHA-256 (hex) van de APK om de download te verifieren, of null. */
    val sha256: String? = null,
)
