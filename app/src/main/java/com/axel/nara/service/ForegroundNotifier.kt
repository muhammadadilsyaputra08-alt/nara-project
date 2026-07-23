package com.axel.nara.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Mengelola channel & notifikasi foreground yang wajib tampil selama AssistantService
 * berjalan (STT/inference/TTS), sesuai rekomendasi: gunakan foreground service untuk
 * proses yang butuh durasi lebih panjang.
 */
class ForegroundNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "nara_assistant_channel"
        const val NOTIFICATION_ID = 1001
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nara Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status asisten suara Nara"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(statusText: String): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Nara")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
