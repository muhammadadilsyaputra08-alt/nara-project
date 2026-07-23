package com.axel.nara.router

import android.content.Context
import com.axel.nara.model.ConnectionRequirement
import com.axel.nara.model.IntentCategory
import com.axel.nara.model.IntentResult
import com.axel.nara.state.ConnectivityState

/**
 * Titik masuk tunggal eksekusi aksi. Menerima IntentResult yang SUDAH divalidasi
 * (lihat utils/JsonValidator.kt), memeriksa kebutuhan koneksi, lalu mendelegasikan
 * ke handler kategori yang sesuai.
 *
 * Aturan routing (sesuai desain):
 * - connection_required = internet tapi offline -> fallback pesan "butuh internet".
 * - device_control tanpa koneksi lokal -> pada scaffold ini device_control selalu "local",
 *   jadi selalu diizinkan (tidak butuh internet).
 * - app_action dengan app tersedia -> buka; app tidak dikenal -> AppNotInstalled -> chat clarify.
 * - web_search online -> jalankan search.
 * - chat -> jawab langsung / minta klarifikasi.
 */
class ActionRouter(context: Context) {

    sealed class RouteOutcome {
        data class Executed(val message: String) : RouteOutcome()
        data class NeedsInternet(val message: String) : RouteOutcome()
        data class Clarify(val message: String) : RouteOutcome()
        data class Failed(val message: String) : RouteOutcome()
    }

    private val connectivity = ConnectivityState(context)
    private val deviceHandler = DeviceActionHandler(context)
    private val appHandler = AppActionHandler(context)
    private val webSearchHandler = WebSearchHandler(context)
    private val chatHandler = ChatFallbackHandler()

    fun route(intent: IntentResult): RouteOutcome {
        // 1. Cek kebutuhan koneksi lebih dulu, sebelum menyentuh handler apa pun.
        if (intent.connection_required == ConnectionRequirement.internet && !connectivity.canRun(intent.connection_required)) {
            return RouteOutcome.NeedsInternet("Perintah ini butuh internet, tuan.")
        }

        return when (intent.category) {
            IntentCategory.device_control -> handleDeviceControl(intent)
            IntentCategory.app_action -> handleAppAction(intent)
            IntentCategory.web_search -> handleWebSearch(intent)
            IntentCategory.chat -> handleChat(intent)
        }
    }

    private fun handleDeviceControl(intent: IntentResult): RouteOutcome =
        when (val result = deviceHandler.handle(intent)) {
            is DeviceActionHandler.Result.Success -> RouteOutcome.Executed(result.message)
            is DeviceActionHandler.Result.Failure -> RouteOutcome.Failed(result.message)
        }

    private fun handleAppAction(intent: IntentResult): RouteOutcome =
        when (val result = appHandler.handle(intent)) {
            is AppActionHandler.Result.Success -> RouteOutcome.Executed(result.message)
            is AppActionHandler.Result.Failure -> RouteOutcome.Failed(result.message)
            AppActionHandler.Result.AppNotInstalled ->
                RouteOutcome.Clarify("Aplikasinya sepertinya belum terpasang, tuan. Mau coba yang lain?")
        }

    private fun handleWebSearch(intent: IntentResult): RouteOutcome =
        when (val result = webSearchHandler.handle(intent)) {
            is WebSearchHandler.Result.Success -> RouteOutcome.Executed(result.message)
            is WebSearchHandler.Result.Failure -> RouteOutcome.Failed(result.message)
        }

    private fun handleChat(intent: IntentResult): RouteOutcome =
        when (val result = chatHandler.handle(intent)) {
            is ChatFallbackHandler.Result.DirectResponse -> RouteOutcome.Executed(result.message)
            is ChatFallbackHandler.Result.Clarify -> RouteOutcome.Clarify(result.message)
        }
}
