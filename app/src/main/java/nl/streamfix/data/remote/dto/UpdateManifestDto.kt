package nl.streamfix.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class UpdateManifestDto(
    @SerialName("versionCode") val versionCode: JsonElement? = null,
    @SerialName("versionName") val versionName: String? = null,
    @SerialName("apkUrl") val apkUrl: String? = null,
    @SerialName("releaseNotes") val releaseNotes: String? = null,
    @SerialName("minSupportedVersionCode") val minSupported: JsonElement? = null,
    @SerialName("forceUpdate") val forceUpdate: JsonElement? = null,
) {
    val versionCodeValue: Int?
        get() = versionCode?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val minSupportedValue: Int?
        get() = minSupported?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val forceUpdateValue: Boolean
        get() = forceUpdate?.jsonPrimitive?.contentOrNull
            ?.let { it == "true" || it == "1" } ?: false
}
