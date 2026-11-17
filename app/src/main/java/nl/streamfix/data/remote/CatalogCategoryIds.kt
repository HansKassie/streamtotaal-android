package nl.streamfix.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun catalogCategoryIds(raw: JsonElement?, primary: String?): Set<String> =
    ((raw as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } +
        listOfNotNull(primary)).filter { it.isNotBlank() }.toSet()
