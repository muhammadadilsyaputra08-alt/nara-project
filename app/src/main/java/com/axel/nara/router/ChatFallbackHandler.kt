package com.axel.nara.router

import com.axel.nara.model.IntentResult
import com.axel.nara.utils.TimeUtils

/**
 * Eksekusi untuk category = chat.
 * Tidak ada aksi sistem — hasilnya murni teks yang dibacakan TtsManager.
 *
 * target = "clarify_intent" -> minta user mengulang/menjelaskan ulang.
 * target = "fallback_chat"  -> obrolan santai / jawaban langsung dari pengetahuan umum.
 *
 * Catatan penting: respons percakapan sesungguhnya (mis. jawaban "ibu kota indonesia apa")
 * dihasilkan oleh model bahasa terpisah (bukan model intent-router ini) atau oleh lapisan LLM
 * cloud/local chat, karena model intent hanya dilatih untuk menghasilkan JSON, bukan jawaban
 * bebas. Handler ini hanya menentukan JENIS respons yang perlu ditampilkan/dibacakan.
 */
class ChatFallbackHandler {

    sealed class Result {
        data class Clarify(val message: String) : Result()
        data class DirectResponse(val message: String) : Result()
    }

    fun handle(intent: IntentResult): Result {
        return when (intent.target) {
            "clarify_intent" -> Result.Clarify("Maaf, tuan, aku belum paham maksudnya. Bisa diulang?")
            "fallback_chat" -> Result.DirectResponse(greetingOrEcho())
            else -> Result.Clarify("Maaf, tuan, aku belum paham maksudnya. Bisa diulang?")
        }
    }

    private fun greetingOrEcho(): String = when (TimeUtils.currentDayPeriod()) {
        TimeUtils.DayPeriod.PAGI -> "Selamat pagi, tuan. Nara siap melayani. Ada yang bisa ku bantu?"
        TimeUtils.DayPeriod.SIANG -> "Selamat siang, tuan. Nara siap melayani. Ada yang bisa ku bantu?"
        TimeUtils.DayPeriod.SORE -> "Selamat sore, tuan. Nara siap melayani. Ada yang bisa ku bantu?"
        TimeUtils.DayPeriod.MALAM -> "Selamat malam, tuan. Nara siap melayani. Ada yang bisa ku bantu?"
    }
}
