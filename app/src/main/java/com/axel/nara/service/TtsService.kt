package com.axel.nara.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import java.util.UUID

/**
 * Wrapper TtsManager berbasis TextToSpeech native Android, dipakai untuk sapaan,
 * transisi ("Tunggu sebentar, tuan...") dan pembacaan hasil aksi.
 */
class TtsService(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("id", "ID")
                isReady = true
            }
        }
    }

    /** Membacakan [text]. Emit true saat selesai (onDone) sekali lalu flow ditutup. */
    fun speak(text: String) = callbackFlow<Boolean> {
        if (!isReady) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                trySend(true)
                close()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                trySend(false)
                close()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        awaitClose { /* tidak stop TTS di sini agar ucapan tetap selesai dibacakan */ }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
