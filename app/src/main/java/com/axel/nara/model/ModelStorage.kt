package com.axel.nara.model

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Lokasi penyimpanan file model GGUF.
 *
 * Sebelumnya pakai `context.filesDir` (internal storage privat) — file di sana HANYA bisa
 * dibaca lewat `run-as` (butuh app debuggable) atau root, jadi kalau perlu cek manual dari
 * Termux (mis. `ls -la`, `sha256sum`, cek ukuran file untuk debug crash loadModel) tidak bisa
 * tanpa root.
 *
 * Sekarang pakai `context.getExternalFilesDir(null)` — tetap direktori privat per-app (app lain
 * tidak bisa baca di Android 11+ karena scoped storage), TAPI bisa diakses tanpa root lewat:
 *   - Termux (`~/storage/shared/Android/data/com.axel.nara2/files/` setelah `termux-setup-storage`)
 *   - File manager mana pun yang menampilkan Android/data
 *   - `adb pull` tanpa perlu app debuggable
 * Tidak perlu permission tambahan (WRITE_EXTERNAL_STORAGE tidak dibutuhkan untuk direktori
 * app-specific ini sejak API 19).
 *
 * File tetap terhapus otomatis saat app di-uninstall, sama seperti sebelumnya.
 */
object ModelStorage {

    const val MODEL_FILE_NAME = "nara-intent-qwen2.5-1.5b.gguf"

    /**
     * Direktori penyimpanan model. Fallback ke internal `filesDir` kalau external storage
     * sedang tidak terpasang (mis. dicabut/emulator tanpa SD card) — sangat jarang terjadi,
     * tapi tanpa fallback ini import model bisa gagal total di kondisi itu.
     */
    fun modelDir(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        return if (externalDir != null && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            externalDir
        } else {
            context.filesDir
        }
    }

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_FILE_NAME)

    fun tempModelFile(context: Context): File = File(modelDir(context), "$MODEL_FILE_NAME.tmp")
}
