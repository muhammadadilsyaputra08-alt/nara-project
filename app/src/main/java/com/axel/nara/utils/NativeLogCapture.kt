package com.axel.nara.utils

import android.util.Log

/**
 * Menangkap baris logcat terbaru dari proses aplikasi sendiri (tidak perlu root/READ_LOGS —
 * Android sejak 4.1 mengizinkan app membaca logcat miliknya sendiri tanpa izin khusus).
 *
 * Dipakai untuk menangkap pesan LOGe/LOGi asli dari native llama.cpp (tag "ai-chat", lihat
 * logging.h di modul :llama) saat loadModel() gagal — supaya diketahui penyebab SEBENARNYA
 * (mis. "unknown model architecture", "failed to open GGUF file", tensor mismatch, dst.),
 * bukan cuma nama exception `UnsupportedArchitectureException` yang menyesatkan.
 */
object NativeLogCapture {

    private val RELEVANT_TAGS = listOf("ai-chat", "llama", "ggml", "InferenceEngineImpl")

    fun captureRecentNativeLogs(maxLines: Int = 200): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-v", "brief", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            output.lineSequence()
                .filter { line -> RELEVANT_TAGS.any { tag -> line.contains(tag) } }
                .toList()
                .takeLast(40)
                .joinToString("\n")
                .ifBlank { "(tidak ada baris logcat relevan ditemukan)" }
        } catch (e: Exception) {
            Log.w("NativeLogCapture", "Gagal mengambil logcat", e)
            "(gagal mengambil logcat: ${e.message})"
        }
    }
}
