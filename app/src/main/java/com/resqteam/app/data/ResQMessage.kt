package com.resqteam.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Raw wire format forwarded by the ESP32 bridge, unchanged from what the
 * ResQMesh civilian packet contains (spec section 21/22).
 *
 * Every field except messageId/type/priority is optional on the wire —
 * some ResQMesh nodes may not have a GPS fix, battery telemetry, etc.
 * We must never invent values for missing optional fields (spec section 8).
 */
@Serializable
data class ResQMessage(
    val messageId: String,
    val sourceNodeId: String,
    val type: String,
    val priority: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long? = null,
    val battery: Int? = null,
    val ttl: Int? = null,
    val hopCount: Int? = null,
    val peopleCount: Int? = null,
    val injuredCount: Int? = null
)

enum class EmergencyPriority(val level: Int, val label: String) {
    CRITICAL(5, "CRITICAL"),
    HIGH(4, "HIGH"),
    MEDIUM(3, "MEDIUM"),
    LOW(2, "LOW"),
    STATUS(1, "STATUS");

    companion object {
        /** Spec section 6: numeric priority from the wire maps to a named tier. */
        fun fromLevel(level: Int): EmergencyPriority =
            entries.firstOrNull { it.level == level } ?: LOW
    }
}

enum class IncidentStatus {
    NEW, ACKNOWLEDGED, RESPONDING, RESCUED, RESOLVED
}

sealed class PacketParseResult {
    data class Success(val message: ResQMessage) : PacketParseResult()
    data class Invalid(val reason: String, val raw: String) : PacketParseResult()
}

/**
 * Parses one newline-delimited JSON line from the ESP32 bridge (spec section 22).
 * Never throws — malformed lines must be discarded, not crash the app (spec section 33/47).
 */
object ResQPacketParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(rawLine: String): PacketParseResult {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) {
            return PacketParseResult.Invalid("empty line", rawLine)
        }
        val message = try {
            json.decodeFromString(ResQMessage.serializer(), trimmed)
        } catch (e: Exception) {
            return PacketParseResult.Invalid("malformed JSON: ${e.message}", rawLine)
        }

        // Structural validation (spec section 47): required fields + sane ranges.
        if (message.messageId.isBlank()) {
            return PacketParseResult.Invalid("missing messageId", rawLine)
        }
        if (message.sourceNodeId.isBlank()) {
            return PacketParseResult.Invalid("missing sourceNodeId", rawLine)
        }
        if (message.priority !in 1..5) {
            return PacketParseResult.Invalid("priority out of range: ${message.priority}", rawLine)
        }
        message.latitude?.let {
            if (it < -90.0 || it > 90.0) return PacketParseResult.Invalid("invalid latitude", rawLine)
        }
        message.longitude?.let {
            if (it < -180.0 || it > 180.0) return PacketParseResult.Invalid("invalid longitude", rawLine)
        }
        message.timestamp?.let {
            if (it < 0) return PacketParseResult.Invalid("invalid timestamp", rawLine)
        }

        return PacketParseResult.Success(message)
    }
}
