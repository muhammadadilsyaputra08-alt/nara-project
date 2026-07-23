package com.axel.nara.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.axel.nara.model.GgufModelEngine
import com.axel.nara.model.ModelEngine
import com.axel.nara.model.ModelInputMapper
import com.axel.nara.router.ActionRouter
import com.axel.nara.state.SessionStore
import com.axel.nara.state.UserPrefs
import com.axel.nara.ui.widget.AssistantWidgetProvider
import com.axel.nara.utils.JsonValidator
import com.axel.nara.utils.NetworkUtils
import com.axel.nara.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service yang mengorkestrasi alur interaksi lengkap:
 * 1. Widget ditekan -> service dipicu (lihat ACTION_MIC_TAP).
 * 2. TTS menyapa berdasarkan waktu.
 * 3. STT menangkap ucapan.
 * 4. ModelEngine menghasilkan JSON intent.
 * 5. JsonValidator memeriksa struktur (retry 1x jika invalid).
 * 6. ActionRouter memutuskan & menjalankan eksekusi.
 * 7. TTS membacakan hasil.
 * 8. Widget diperbarui dengan status akhir via broadcast APPWIDGET_UPDATE.
 *
 * Model GGUF disimpan di direktori external app-specific (lihat [com.axel.nara.model.ModelStorage])
 * — bisa dicek manual lewat Termux/file manager tanpa root, beda dengan internal filesDir lama.
 */
class AssistantService : Service() {

    companion object {
        const val ACTION_START_SESSION = "com.axel.nara.ACTION_START_SESSION"
        /** Dipicu QuickInputActivity: proses teks langsung, skip greeting & STT. */
        const val ACTION_PROCESS_TEXT = "com.axel.nara.ACTION_PROCESS_TEXT"
        const val EXTRA_TEXT = "com.axel.nara.EXTRA_TEXT"
        private const val MAX_RETRY = 1
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var modelEngine: ModelEngine
    private lateinit var speechService: SpeechService
    private lateinit var ttsService: TtsService
    private lateinit var notifier: ForegroundNotifier
    private lateinit var actionRouter: ActionRouter
    private lateinit var sessionStore: SessionStore
    private lateinit var userPrefs: UserPrefs

    // FIX akar masalah crash "IllegalStateException: Cannot load model in ModelReady!"
    // (log ModelInference): onStartCommand() sebelumnya me-launch coroutine baru setiap
    // intent masuk TANPA membatalkan sesi sebelumnya. Tap widget 2x cepat, atau STT selesai
    // bersamaan dengan ACTION_PROCESS_TEXT dari QuickInputActivity, menghasilkan 2
    // processUtterance() berjalan paralel -> keduanya lolos check modelEngine.isReady()==false
    // -> keduanya panggil load() ke singleton native yang sama -> crash. Job tunggal ini
    // memastikan hanya satu sesi/utterance yang diproses di satu waktu.
    private var activeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        modelEngine = GgufModelEngine(applicationContext)
        speechService = SpeechService(applicationContext)
        ttsService = TtsService(applicationContext)
        notifier = ForegroundNotifier(applicationContext)
        actionRouter = ActionRouter(applicationContext)
        sessionStore = SessionStore()
        userPrefs = UserPrefs(applicationContext)

        notifier.ensureChannel()

        sessionStore.snapshot
            .onEach { snapshot -> AssistantWidgetProvider.notifyStateChanged(applicationContext, snapshot) }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notifier.buildNotification(getString(com.axel.nara.R.string.status_idle))
        ServiceCompat.startForeground(
            this,
            ForegroundNotifier.NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        when (intent?.action) {
            ACTION_START_SESSION -> {
                activeJob?.cancel()
                activeJob = serviceScope.launch { runSession() }
            }
            ACTION_PROCESS_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    activeJob?.cancel()
                    activeJob = serviceScope.launch { processUtterance(text) }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.launch { modelEngine.unload() }
        ttsService.shutdown()
        speechService.cancel()
        super.onDestroy()
    }

    private suspend fun runSession() {
        // 2. Sapaan berbasis waktu
        sessionStore.transitionTo(SessionStore.AssistantState.GREETING)
        val greeting = greetingForNow()
        sessionStore.setLastResponse(greeting)
        ttsService.speak(greeting).first()

        // 3. STT
        sessionStore.transitionTo(SessionStore.AssistantState.LISTENING)
        val sttResult = captureSpeech() ?: run {
            sessionStore.transitionTo(SessionStore.AssistantState.ERROR)
            ttsService.speak("Maaf, tuan, suaranya tidak tertangkap.").first()
            sessionStore.transitionTo(SessionStore.AssistantState.IDLE)
            return
        }

        processUtterance(sttResult)
    }

    private suspend fun processUtterance(userText: String) {
        sessionStore.transitionTo(SessionStore.AssistantState.PROCESSING)
        sessionStore.resetRetry()

        val isOnline = NetworkUtils.isOnline(applicationContext)
        val prompt = ModelInputMapper.buildPrompt(
            ModelInputMapper.ModelContext(rawSttText = userText, isOnline = isOnline)
        )

        val validated: JsonValidator.ValidationResult
        try {
            if (!modelEngine.isReady()) {
                val modelPath = com.axel.nara.model.ModelStorage.modelFile(applicationContext).absolutePath
                modelEngine.load(modelPath)
            }

            var result = JsonValidator.validate(modelEngine.generate(prompt))
            var attempt = 0
            while (result is JsonValidator.ValidationResult.Invalid && attempt < MAX_RETRY) {
                attempt++
                // Reset context native sebelum retry: mengirim ulang prompt yang SAMA ke context
                // yang sudah tercemar output rusak dari attempt sebelumnya nyaris pasti gagal lagi
                // (lihat crash log JsonInvalid — degenerasi "isposable" berulang). unload()+load()
                // memakai API yang sudah terverifikasi benar di GgufModelEngine, hasilnya context
                // bersih untuk attempt kedua.
                val modelPath = com.axel.nara.model.ModelStorage.modelFile(applicationContext).absolutePath
                modelEngine.unload()
                modelEngine.load(modelPath)
                result = JsonValidator.validate(modelEngine.generate(prompt))
            }
            validated = result
        } catch (e: com.axel.nara.model.ModelLoadException) {
            com.axel.nara.utils.CrashLogger.log(applicationContext, "ModelLoad", e)
            sessionStore.transitionTo(SessionStore.AssistantState.ERROR)
            val message = "Maaf, tuan. ${e.userMessage}"
            sessionStore.setLastResponse(message)
            ttsService.speak(message).first()
            sessionStore.transitionTo(SessionStore.AssistantState.IDLE)
            return
        } catch (e: Exception) {
            com.axel.nara.utils.CrashLogger.log(applicationContext, "ModelInference", e)
            sessionStore.transitionTo(SessionStore.AssistantState.ERROR)
            val message = "Maaf, tuan, ada kendala saat memproses permintaan."
            sessionStore.setLastResponse(message)
            ttsService.speak(message).first()
            sessionStore.transitionTo(SessionStore.AssistantState.IDLE)
            return
        }

        when (validated) {
            is JsonValidator.ValidationResult.Invalid -> {
                com.axel.nara.utils.CrashLogger.log(
                    applicationContext,
                    "JsonInvalid",
                    "Input: $userText\nAlasan: ${validated.reason}\nRaw output model: ${validated.rawOutput}"
                )
                sessionStore.transitionTo(SessionStore.AssistantState.FALLBACK)
                val message = "Maaf, tuan, aku belum paham maksudnya. Bisa diulang?"
                sessionStore.setLastResponse(message)
                ttsService.speak(message).first()
            }
            is JsonValidator.ValidationResult.Valid -> {
                val intent = validated.intent
                sessionStore.setLastIntent(intent)

                // Beri transisi "tunggu sebentar" hanya untuk aksi yang butuh internet/pencarian mendalam.
                if (intent.connection_required == com.axel.nara.model.ConnectionRequirement.internet) {
                    ttsService.speak(getString(com.axel.nara.R.string.transition_deep_search)).first()
                }

                sessionStore.transitionTo(SessionStore.AssistantState.EXECUTING)
                val outcome = actionRouter.route(intent)

                val responseText = when (outcome) {
                    is ActionRouter.RouteOutcome.Executed -> outcome.message
                    is ActionRouter.RouteOutcome.NeedsInternet -> outcome.message
                    is ActionRouter.RouteOutcome.Clarify -> outcome.message
                    is ActionRouter.RouteOutcome.Failed -> outcome.message
                }

                sessionStore.setLastResponse(responseText)
                sessionStore.appendHistory(
                    SessionStore.HistoryEntry(
                        input = userText,
                        intent = intent,
                        responseText = responseText,
                        timestampIso = TimeUtils.currentTimeIso()
                    )
                )

                sessionStore.transitionTo(SessionStore.AssistantState.SPEAKING)
                ttsService.speak(responseText).first()
            }
        }

        sessionStore.transitionTo(SessionStore.AssistantState.IDLE)
    }

    private suspend fun captureSpeech(): String? {
        var finalText: String? = null
        speechService.listen().onEach { event ->
            when (event) {
                is SpeechService.SttEvent.FinalResult -> finalText = event.text
                else -> Unit
            }
        }.launchIn(serviceScope).join()
        return finalText
    }

    private fun greetingForNow(): String = when (TimeUtils.currentDayPeriod()) {
        TimeUtils.DayPeriod.PAGI -> getString(com.axel.nara.R.string.greeting_pagi)
        TimeUtils.DayPeriod.SIANG -> getString(com.axel.nara.R.string.greeting_siang)
        TimeUtils.DayPeriod.SORE -> getString(com.axel.nara.R.string.greeting_sore)
        TimeUtils.DayPeriod.MALAM -> getString(com.axel.nara.R.string.greeting_malam)
    }
}
