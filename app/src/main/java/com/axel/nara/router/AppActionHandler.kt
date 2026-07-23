package com.axel.nara.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.axel.nara.model.IntentResult

/**
 * Eksekusi untuk category = app_action.
 * Prioritas: deep link/feature spesifik jika tersedia -> fallback ke launcher app biasa.
 * App tambahan yang tidak ada di peta [KNOWN_APP_PACKAGES] tetap dibuka lewat launcher umum
 * selama nama app cocok dengan aplikasi yang benar-benar terpasang (dicek via PackageManager).
 */
class AppActionHandler(private val context: Context) {

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
        object AppNotInstalled : Result()
    }

    /** Peta target dataset -> package name asli. Tambahkan sesuai app yang ingin didukung penuh. */
    private val knownPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "tiktok" to "com.zhiliaoapp.musically",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm",
        "spotify" to "com.spotify.music",
        "google_maps" to "com.google.android.apps.maps",
        "telegram" to "org.telegram.messenger",
        "browser" to "com.android.chrome",
        "calculator" to "com.google.android.calculator",
        "notes" to "com.google.android.keep",
        "files" to "com.google.android.documentsui",
        "clock" to "com.google.android.deskclock",
        "camera" to "com.google.android.GoogleCamera",
        "gallery" to "com.google.android.apps.photos",
        "settings" to "com.android.settings",
        "calendar" to "com.google.android.calendar",
        "recorder" to "com.google.android.apps.recorder"
    )

    fun handle(intent: IntentResult): Result {
        val target = intent.target

        // App eksplisit tidak dikenal sistem -> fallback ke chat clarifying (ditangani ActionRouter).
        if (target == "unknown_app") {
            return Result.AppNotInstalled
        }

        return when (intent.action) {
            "open_app" -> openApp(target)
            "search_and_play" -> openYoutubeSearch(intent.payloadString("query"))
            "search", "query" -> openGenericSearch(target, intent.payloadString("query"))
            "open_url" -> openUrl(intent.payloadString("url"))
            "open_chat" -> openWhatsappChat(intent.payloadString("contact"))
            "navigate" -> openMapsNavigation(intent.payloadString("destination"))
            "open_feature" -> openApp(target) // fallback aman: buka app-nya, biarkan user navigasi manual ke fitur
            "set_alarm" -> setAlarm(intent)
            "set_timer" -> setTimer(intent)
            "create_note" -> createNote(intent)
            "create_event", "schedule_event", "add_event", "set_reminder" -> createCalendarEvent(intent)
            "open_folder", "open_recent", "start_recording", "stop_recording",
            "call", "play", "open_reels", "open_dm", "open_stories" ->
                openApp(target) // aksi spesifik internal app lain; scaffold ini buka app-nya dulu
            else -> openApp(target)
        }
    }

    /**
     * Ambil nilai payload pertama yang ada dari beberapa kemungkinan nama key — model intent
     * bisa saja dilatih pakai nama field Inggris ATAU Indonesia, jadi kita coba beberapa.
     */
    private fun IntentResult.firstPayload(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> payloadString(key)?.takeIf { it.isNotBlank() } }

    private fun setAlarm(intent: IntentResult): Result {
        // Skema dataset asli (intent_dataset_v3.jsonl): payload = {"time": "HH:MM"}, bukan
        // hour/minute terpisah. Tetap sediakan fallback hour/minute untuk jaga-jaga kalau
        // versi dataset berikutnya berubah format.
        val timeStr = intent.firstPayload("time", "waktu")
        val (hour, minute) = if (timeStr != null) {
            val parts = timeStr.split(":")
            val h = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            h to m
        } else {
            intent.firstPayload("hour", "jam")?.toIntOrNull() to
                (intent.firstPayload("minute", "menit")?.toIntOrNull() ?: 0)
        }
        val label = intent.firstPayload("message", "label", "title", "judul", "catatan")

        if (hour == null) {
            // Tidak ada jam spesifik disebut -> buka daftar alarm, biar user atur manual.
            val showIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (showIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(showIntent)
                Result.Success("Membuka daftar alarm (jam tidak disebutkan).")
            } else {
                openApp("clock")
            }
        }

        val alarmIntent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
            putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
            if (!label.isNullOrBlank()) putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true) // langsung set tanpa buka UI app Jam
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (alarmIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(alarmIntent)
            Result.Success("Alarm diatur jam %02d:%02d".format(hour, minute))
        } else {
            Result.Failure("Tidak ada aplikasi jam yang bisa menangani permintaan set alarm.")
        }
    }

    private fun setTimer(intent: IntentResult): Result {
        val seconds = intent.firstPayload("duration_seconds", "durasi_detik")?.toIntOrNull()
            ?: run {
                val minutes = intent.firstPayload("duration_minutes", "menit", "durasi")?.toIntOrNull()
                minutes?.let { it * 60 }
            }
        val label = intent.firstPayload("message", "label", "title", "judul")

        val timerIntent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            if (seconds != null) putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
            if (!label.isNullOrBlank()) putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, seconds != null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (timerIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(timerIntent)
            Result.Success(if (seconds != null) "Timer diatur $seconds detik" else "Membuka pengatur timer")
        } else {
            Result.Failure("Tidak ada aplikasi jam yang bisa menangani permintaan set timer.")
        }
    }

    /**
     * Tidak ada Intent sistem universal untuk "buat catatan" (beda dari alarm/kalender yang
     * memang bagian dari AOSP framework) — tiap app notes punya API sendiri-sendiri. Google Keep
     * mendukung ACTION_SEND teks polos yang otomatis jadi draft catatan baru, jadi kita pakai itu
     * sebagai jalan paling kompatibel; fallback buka app notes kosong kalau tidak ada isi/app lain.
     */
    private fun createNote(intent: IntentResult): Result {
        val content = intent.firstPayload("content", "text", "note", "catatan", "isi")
        if (content.isNullOrBlank()) return openApp("notes")

        val notesPackage = knownPackages["notes"]
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
            setPackage(notesPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (sendIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(sendIntent)
            Result.Success("Catatan dibuat: $content")
        } else {
            openApp("notes")
        }
    }

    /** "Atur jadwal" -> buat event kalender lewat ACTION_INSERT (tidak butuh izin WRITE_CALENDAR). */
    private fun createCalendarEvent(intent: IntentResult): Result {
        val title = intent.firstPayload("title", "judul", "message", "label") ?: "Jadwal baru"
        val startMillis = intent.firstPayload("start_time_millis")?.toLongOrNull()

        val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, title)
            if (startMillis != null) {
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 60 * 60 * 1000)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (calendarIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(calendarIntent)
            Result.Success("Membuka form jadwal: $title")
        } else {
            Result.Failure("Tidak ada aplikasi kalender terpasang.")
        }
    }

    private fun openApp(target: String): Result {
        val packageName = knownPackages[target] ?: target
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Result.Success("Membuka aplikasi: $target")
        } else {
            Result.AppNotInstalled
        }
    }

    private fun openYoutubeSearch(query: String?): Result {
        if (query.isNullOrBlank()) return openApp("youtube")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Result.Success("Mencari di YouTube: $query")
    }

    private fun openGenericSearch(target: String, query: String?): Result {
        if (query.isNullOrBlank()) return openApp(target)
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return Result.Success("Mencari di $target: $query")
    }

    private fun openUrl(url: String?): Result {
        if (url.isNullOrBlank()) return Result.Failure("URL kosong untuk open_url")
        val safeUrl = if (url.startsWith("http")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Result.Success("Membuka URL: $safeUrl")
    }

    private fun openWhatsappChat(contact: String?): Result {
        // Deep link nomor tidak tersedia dari nama kontak saja; scaffold membuka app WhatsApp
        // dan menyerahkan pemilihan kontak ke user. Integrasi ContactsContract bisa ditambahkan.
        return openApp("whatsapp")
    }

    private fun openMapsNavigation(destination: String?): Result {
        if (destination.isNullOrBlank()) return openApp("google_maps")
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        ).apply {
            setPackage(knownPackages["google_maps"])
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Result.Success("Navigasi ke: $destination")
        } else {
            openApp("google_maps")
        }
    }
}
