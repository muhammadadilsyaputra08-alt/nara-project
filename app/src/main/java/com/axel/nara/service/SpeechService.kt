package com.axel.nara.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wrapper tipis di atas SpeechRecognizer bawaan Android (STT native, sesuai desain:
 * "STT native Android untuk input suara").
 */
class SpeechService(private val context: Context) {

    sealed class SttEvent {
        object ReadyForSpeech : SttEvent()
        data class PartialResult(val text: String) : SttEvent()
        data class FinalResult(val text: String) : SttEvent()
        data class Error(val message: String) : SttEvent()
    }

    private var recognizer: SpeechRecognizer? = null

    fun listen() = callbackFlow<SttEvent> {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SttEvent.Error("SpeechRecognizer tidak tersedia di device ini."))
            close()
            return@callbackFlow
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SttEvent.ReadyForSpeech)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = extractBestText(partialResults)
                    if (!text.isNullOrBlank()) trySend(SttEvent.PartialResult(text))
                }

                override fun onResults(results: Bundle?) {
                    val text = extractBestText(results)
                    if (!text.isNullOrBlank()) {
                        trySend(SttEvent.FinalResult(text))
                    } else {
                        trySend(SttEvent.Error("Tidak ada hasil pengenalan suara."))
                    }
                    close()
                }

                override fun onError(error: Int) {
                    trySend(SttEvent.Error("STT error code: $error"))
                    close()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            startListening(intent)
        }

        awaitClose {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun extractBestText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
