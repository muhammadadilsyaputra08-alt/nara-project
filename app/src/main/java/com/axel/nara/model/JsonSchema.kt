package com.axel.nara.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Skema output ModelEngine, sesuai desain dataset intent-router:
 * {
 *   "category": "device_control | app_action | web_search | chat",
 *   "connection_required": "local | internet | none",
 *   "target": "string",
 *   "action": "string",
 *   "payload": {}
 * }
 */

enum class IntentCategory {
    device_control, app_action, web_search, chat
}

enum class ConnectionRequirement {
    local, internet, none
}

@Serializable
data class IntentResult(
    val category: IntentCategory,
    val connection_required: ConnectionRequirement,
    val target: String,
    val action: String,
    val payload: JsonObject = JsonObject(emptyMap())
) {
    fun payloadString(key: String): String? =
        (payload[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

    fun payloadElement(key: String): JsonElement? = payload[key]
}
