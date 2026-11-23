package nl.streamfix.data.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal fun catalogTimestamp(value: JsonElement?): Long? {
    val raw = (value as? JsonPrimitive)?.content?.trim()?.toLongOrNull() ?: return null
    if (raw <= 0) return null
    return if (raw >= 1_000_000_000_000L) raw / 1000 else raw
}
