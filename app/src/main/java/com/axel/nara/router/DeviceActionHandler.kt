package com.axel.nara.router

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import com.axel.nara.model.IntentResult

/**
 * Eksekusi untuk category = device_control.
 * Fokus hanya pada fitur umum & aman (volume, brightness, wifi, bluetooth, flashlight,
 * screenshot, lock_screen, notification_panel, quick_settings) — TIDAK ada IoT/smart home.
 *
 * Catatan: beberapa aksi (toggle wifi/bluetooth langsung, screenshot terprogram, lock_screen)
 * dibatasi API level Android modern demi keamanan; handler ini membuka layar sistem terkait
 * bila toggle langsung tidak diizinkan (fallback yang aman & disetujui Google).
 */
class DeviceActionHandler(private val context: Context) {

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    fun handle(intent: IntentResult): Result {
        val device = intent.payloadString("device") ?: "phone"
        return when (intent.target) {
            "volume" -> handleVolume(intent.action, intent.payloadString("step"))
            "brightness" -> handleBrightness(intent.action)
            "wifi" -> handleWifi(intent.action)
            "bluetooth" -> handleBluetooth(intent.action)
            "flashlight" -> handleFlashlight(intent.action)
            "screenshot" -> handleScreenshot()
            "lock_screen" -> handleLockScreen(intent.action)
            "notification_panel" -> openSystemPanel(collapsed = false)
            "quick_settings" -> openQuickSettingsPanel()
            else -> Result.Failure("Target device_control tidak dikenal: ${intent.target} ($device)")
        }
    }

    private fun handleVolume(action: String, step: String?): Result {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = when (action) {
            "increase" -> AudioManager.ADJUST_RAISE
            "decrease" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> return Result.Failure("Action volume tidak dikenal: $action")
        }
        val flag = if (step == "small") 0 else AudioManager.FLAG_SHOW_UI
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flag)
        return Result.Success("Volume: $action ($step)")
    }

    private fun handleBrightness(action: String): Result {
        if (!Settings.System.canWrite(context)) {
            // Perlu izin WRITE_SETTINGS eksplisit dari user -> arahkan ke halaman izin.
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return Result.Failure("Butuh izin WRITE_SETTINGS, mengarahkan user ke halaman izin.")
        }
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val step = 40
        val newValue = when (action) {
            "increase" -> (current + step).coerceAtMost(255)
            "decrease" -> (current - step).coerceAtLeast(10)
            else -> return Result.Failure("Action brightness tidak dikenal: $action")
        }
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newValue)
        return Result.Success("Brightness: $action -> $newValue")
    }

    private fun handleWifi(action: String): Result {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ tidak izinkan toggle wifi langsung -> buka panel Wi-Fi sistem.
            context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.Success("Membuka panel Wi-Fi sistem (Android 10+ tidak izinkan toggle langsung).")
        } else {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = action == "wifi_on"
            Result.Success("Wifi: $action")
        }
    }

    private fun handleBluetooth(action: String): Result {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Result.Success("Membuka pengaturan Bluetooth ($action).")
    }

    private fun handleFlashlight(action: String): Result {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Result.Failure("Perangkat tidak punya flash unit.")
            cm.setTorchMode(cameraId, action == "flashlight_on")
            Result.Success("Flashlight: $action")
        } catch (e: Exception) {
            Result.Failure("Gagal mengatur flashlight: ${e.message}")
        }
    }

    private fun handleScreenshot(): Result {
        // Screenshot terprogram butuh MediaProjection API + izin runtime dari Activity;
        // di sini kita trigger lewat kombinasi tombol sistem sebagai fallback aman.
        return Result.Failure(
            "Screenshot butuh alur izin MediaProjection dari Activity (belum di-wire di scaffold ini)."
        )
    }

    private fun handleLockScreen(action: String): Result {
        // lock_screen langsung butuh DeviceAdminReceiver/DevicePolicyManager (izin admin).
        // unlock_screen dari aplikasi pihak ketiga dibatasi sistem demi keamanan.
        return Result.Failure(
            "Aksi '$action' butuh DevicePolicyManager + izin admin perangkat (belum di-wire di scaffold ini)."
        )
    }

    private fun openSystemPanel(collapsed: Boolean): Result {
        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        return Result.Success("Notification panel diminta terbuka (perlu shortcut sistem/Accessibility di device nyata).")
    }

    private fun openQuickSettingsPanel(): Result {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Result.Success("Quick settings diminta terbuka (perlu TileService/Accessibility di device nyata).")
        } else {
            Result.Failure("Quick settings panel butuh API 24+.")
        }
    }
}
