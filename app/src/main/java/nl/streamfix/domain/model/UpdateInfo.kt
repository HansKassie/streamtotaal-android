package nl.streamfix.domain.model

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val mandatory: Boolean,
)
