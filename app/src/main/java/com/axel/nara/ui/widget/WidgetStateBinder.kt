package com.axel.nara.ui.widget

import android.content.Context
import android.widget.RemoteViews
import com.axel.nara.R
import com.axel.nara.state.ConnectivityState
import com.axel.nara.state.SessionStore
import com.axel.nara.utils.NetworkUtils

/**
 * Menerjemahkan SessionStore.SessionSnapshot menjadi teks/warna yang ditampilkan
 * di RemoteViews widget (compact atau expanded).
 */
object WidgetStateBinder {

    fun bind(context: Context, views: RemoteViews, snapshot: SessionStore.SessionSnapshot) {
        runCatching { views.setTextViewText(R.id.tv_status, statusText(context, snapshot.state)) }

        val displayText = snapshot.lastResponseText.ifBlank { context.getString(R.string.widget_hint_placeholder) }
        runCatching { views.setTextViewText(R.id.tv_last_response, displayText) }

        val connected = NetworkUtils.isOnline(context)
        val indicatorColor = if (connected) R.color.nara_status_online else R.color.nara_status_offline
        runCatching { views.setInt(R.id.indicator_connection, "setBackgroundColor", context.getColor(indicatorColor)) }

        // Tombol retry hanya tampil saat state ERROR/FALLBACK (hanya ada di layout expanded)
        val showRetry = snapshot.state == SessionStore.AssistantState.ERROR ||
            snapshot.state == SessionStore.AssistantState.FALLBACK
        runCatching {
            views.setViewVisibility(
                R.id.btn_retry,
                if (showRetry) android.view.View.VISIBLE else android.view.View.GONE
            )
        }
    }

    private fun statusText(context: Context, state: SessionStore.AssistantState): String = when (state) {
        SessionStore.AssistantState.IDLE -> context.getString(R.string.status_idle)
        SessionStore.AssistantState.GREETING -> context.getString(R.string.status_idle)
        SessionStore.AssistantState.LISTENING -> context.getString(R.string.status_listening)
        SessionStore.AssistantState.PROCESSING -> context.getString(R.string.status_processing)
        SessionStore.AssistantState.EXECUTING -> context.getString(R.string.status_executing)
        SessionStore.AssistantState.SPEAKING -> context.getString(R.string.status_speaking)
        SessionStore.AssistantState.ERROR -> context.getString(R.string.status_error)
        SessionStore.AssistantState.FALLBACK -> context.getString(R.string.status_fallback)
    }
}
