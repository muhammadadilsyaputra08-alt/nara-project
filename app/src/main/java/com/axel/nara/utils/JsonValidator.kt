package com.axel.nara.utils

import com.axel.nara.model.IntentResult
import kotlinx.serialization.json.Json

/**
 * Memvalidasi & mem-parsing teks mentah hasil ModelEngine menjadi IntentResult.
 * Dipakai AssistantService sebelum meneruskan hasil ke ActionRouter.
 *
 * Aturan:
 * - Harus JSON valid.
 * - category & connection_required harus salah satu nilai enum yang dikenal.
 * - target & action tidak boleh kosong.
 */
object JsonValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    sealed class ValidationResult {
        data class Valid(val intent: IntentResult) : ValidationResult()
        data class Invalid(val reason: String, val rawOutput: String) : ValidationResult()
    }

    fun validate(rawModelOutput: String): ValidationResult {
        val jsonText = extractJsonObject(rawModelOutput)
            ?: return ValidationResult.Invalid("Tidak ditemukan objek JSON pada output model", rawModelOutput)

        return try {
            val intent = json.decodeFromString(IntentResult.serializer(), jsonText)
            if (intent.target.isBlank() || intent.action.isBlank()) {
                ValidationResult.Invalid("target/action kosong", rawModelOutput)
            } else {
                ValidationResult.Valid(intent)
            }
        } catch (e: Exception) {
            ValidationResult.Invalid("JSON tidak sesuai skema: ${e.message}", rawModelOutput)
        }
    }

    /** Ambil substring `{...}` pertama dari output model (model bisa saja menambah teks lain). */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
