package com.axel.nara.state

import com.axel.nara.model.IntentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * State machine sesi asisten:
 * Idle -> Greeting -> Listening -> Processing -> Executing -> Speaking -> (Idle)
 * dengan cabang Error / Fallback jika validasi/eksekusi gagal.
 *
 * Widget & AssistantService membaca [state] untuk memutuskan teks status & tombol yang tampil.
 */
class SessionStore {

    enum class AssistantState {
        IDLE, GREETING, LISTENING, PROCESSING, EXECUTING, SPEAKING, ERROR, FALLBACK
    }

    data class HistoryEntry(
        val input: String,
        val intent: IntentResult?,
        val responseText: String,
        val timestampIso: String
    )

    data class SessionSnapshot(
        val state: AssistantState = AssistantState.IDLE,
        val lastResponseText: String = "",
        val lastIntent: IntentResult? = null,
        val retryCount: Int = 0,
        val history: List<HistoryEntry> = emptyList()
    )

    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot

    fun transitionTo(state: AssistantState) {
        _snapshot.update { it.copy(state = state) }
    }

    fun setLastResponse(text: String) {
        _snapshot.update { it.copy(lastResponseText = text) }
    }

    fun setLastIntent(intent: IntentResult?) {
        _snapshot.update { it.copy(lastIntent = intent) }
    }

    fun incrementRetry(): Int {
        var newCount = 0
        _snapshot.update {
            newCount = it.retryCount + 1
            it.copy(retryCount = newCount)
        }
        return newCount
    }

    fun resetRetry() {
        _snapshot.update { it.copy(retryCount = 0) }
    }

    fun appendHistory(entry: HistoryEntry) {
        _snapshot.update { current ->
            val trimmed = (current.history + entry).takeLast(MAX_HISTORY)
            current.copy(history = trimmed)
        }
    }

    companion object {
        private const val MAX_HISTORY = 20
    }
}
