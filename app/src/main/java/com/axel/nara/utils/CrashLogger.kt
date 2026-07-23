package com.axel.nara.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menulis crash/error log ke folder Download publik supaya bisa dibaca dari Termux
 * tanpa root (Termux tidak bisa baca logcat/app lain tanpa akses root).
 * Hasil: /storage/emulated/0/Download/nara_crash_<timestamp>.log
 */
object CrashLogger {

    fun log(context: Context, tag: String, throwable: Throwable) {
        log(context, tag, Log.getStackTraceString(throwable))
    }

    fun log(context: Context, tag: String, message: String) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "nara_crash_${stamp}.log"
        val content = buildString {
            appendLine("== $tag ==")
            appendLine("Waktu: ${Date()}")
            appendLine(message)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { out -> out.write(content.toByteArray()) }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(content.toByteArray()) }
            }
        } catch (e: Exception) {
            // Kalau penulisan log pun gagal, jangan sampai bikin crash tambahan.
            Log.e("CrashLogger", "Gagal menulis crash log", e)
        }

        // Tetap kirim ke Logcat juga untuk kasus yang bisa dibaca lewat Android Studio.
        Log.e(tag, message)
    }
}
