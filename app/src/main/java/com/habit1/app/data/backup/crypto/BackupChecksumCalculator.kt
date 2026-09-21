package com.habit1.app.data.backup.crypto

import com.habit1.app.data.backup.model.BackupPayloadDto
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Calculates and verifies deterministic SHA-256 checksums for backup payloads.
 *
 * NOTE: SHA-256 provides integrity verification (tamper and corruption detection).
 * It does NOT provide encryption or confidentiality.
 */
object BackupChecksumCalculator {

    /**
     * Fixed, canonical Json configuration to ensure byte-exact deterministic serialization.
     * Whitespace is omitted (prettyPrint = false) and all default values are encoded.
     */
    val CanonicalJson = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    /**
     * Sorts all payload collections in a deterministic order before serialization.
     */
    fun sortPayload(payload: BackupPayloadDto): BackupPayloadDto {
        return BackupPayloadDto(
            habits = payload.habits.sortedBy { it.id },
            records = payload.records.sortedWith(compareBy({ it.habitId }, { it.date })),
            goals = payload.goals.sortedBy { it.id },
            subtasks = payload.subtasks.sortedWith(compareBy({ it.goalId }, { it.id })),
            reviews = payload.reviews.sortedBy { it.date }
        )
    }

    /**
     * Serializes the payload to its canonical JSON string representation.
     */
    fun toCanonicalJson(payload: BackupPayloadDto): String {
        val sortedPayload = sortPayload(payload)
        return CanonicalJson.encodeToString(BackupPayloadDto.serializer(), sortedPayload)
    }

    /**
     * Computes the SHA-256 hex digest of the canonicalized payload.
     */
    fun computeChecksum(payload: BackupPayloadDto): String {
        val canonicalJson = toCanonicalJson(payload)
        val bytes = canonicalJson.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that the provided hex checksum matches the payload's computed checksum using
     * constant-time byte comparison.
     */
    fun verifyChecksum(payload: BackupPayloadDto, expectedChecksum: String): Boolean {
        val computedChecksum = computeChecksum(payload)
        val expectedBytes = expectedChecksum.lowercase().toByteArray(Charsets.UTF_8)
        val computedBytes = computedChecksum.lowercase().toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, computedBytes)
    }
}
