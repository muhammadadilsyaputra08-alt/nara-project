package com.axel.nara.model

import com.axel.nara.utils.TimeUtils

/**
 * Menyusun prompt akhir yang dikirim ke ModelEngine, dari teks mentah STT + konteks
 * (waktu, status koneksi, intent terakhir bila relevan).
 *
 * Format prompt SENGAJA sama dengan format training (lihat notebook fine-tuning):
 *   "Ucapan: <input>\nJSON: "
 * agar distribusi input saat inferensi cocok dengan distribusi saat training.
 */
object ModelInputMapper {

    private const val PROMPT_TEMPLATE = "Ucapan: %s\nJSON: "

    data class ModelContext(
        val rawSttText: String,
        val isOnline: Boolean,
        val dayPeriod: TimeUtils.DayPeriod = TimeUtils.currentDayPeriod(),
        val lastIntentTarget: String? = null
    )

    /**
     * Prompt inti yang dipakai model untuk menghasilkan JSON.
     * Konteks (online/offline, waktu, last intent) TIDAK disisipkan ke dalam teks prompt,
     * karena model hanya dilatih pada pemetaan ucapan->JSON murni; konteks itu dipakai
     * di layer aplikasi (ActionRouter) untuk memutuskan eksekusi, bukan oleh model.
     */
    fun buildPrompt(context: ModelContext): String {
        val normalized = normalize(context.rawSttText)
        return PROMPT_TEMPLATE.format(normalized)
    }

    private fun normalize(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")
}
