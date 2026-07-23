package com.axel.nara.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.axel.nara.state.SessionStore

/**
 * Entry point widget: menangani update UI, tombol mic, shortcut, dan refresh.
 * Semua proses berat (STT/inference/TTS) didelegasikan ke AssistantService agar
 * widget tetap ringan & responsif (sesuai arsitektur sistem).
 */
class AssistantWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_MIC_TAP = "com.axel.nara.ACTION_MIC_TAP"
        const val ACTION_SETTINGS_TAP = "com.axel.nara.ACTION_SETTINGS_TAP"
        const val ACTION_TOGGLE_MODE = "com.axel.nara.ACTION_TOGGLE_MODE"
        const val ACTION_RETRY = "com.axel.nara.ACTION_RETRY"

        private var lastSnapshot: SessionStore.SessionSnapshot = SessionStore.SessionSnapshot()

        /** Dipanggil AssistantService setiap kali SessionStore berubah, untuk refresh semua instance widget. */
        fun notifyStateChanged(context: Context, snapshot: SessionStore.SessionSnapshot) {
            lastSnapshot = snapshot
            refreshAllWidgets(context)
        }

        /** Refresh semua instance widget tanpa mengubah snapshot sesi (mis. setelah ganti preferensi). */
        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, AssistantWidgetProvider::class.java)
            )
            val provider = AssistantWidgetProvider()
            ids.forEach { id -> provider.updateWidget(context, manager, id) }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    /**
     * Dipanggil launcher saat widget baru ditambahkan (dengan ukuran awal) ATAU di-resize manual.
     * Fix untuk "ikon baru muncul setelah resize": beberapa launcher memanggil callback ini
     * TANPA memanggil onUpdate() lagi, jadi tanpa handler ini render pertama bisa memakai
     * RemoteViews yang belum lengkap sampai ada trigger lain. Selalu re-render penuh di sini.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        when (intent.action) {
            ACTION_MIC_TAP -> AssistantWidgetRemoteViews.startAssistantSession(context)
            ACTION_SETTINGS_TAP -> { /* dibuka langsung via PendingIntent activity, tidak perlu handle di sini */ }
            ACTION_TOGGLE_MODE -> toggleExpandedMode(context, appWidgetId)
            ACTION_RETRY -> AssistantWidgetRemoteViews.startAssistantSession(context)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        try {
            val expanded = com.axel.nara.state.WidgetPrefs.isExpandedMode(context)
            val views = AssistantWidgetRemoteViews.build(context, appWidgetId, expanded, lastSnapshot)
            manager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            com.axel.nara.utils.CrashLogger.log(context, "NaraWidget", e)
            try {
                val fallback = android.widget.RemoteViews(context.packageName, com.axel.nara.R.layout.widget_compact)
                manager.updateAppWidget(appWidgetId, fallback)
            } catch (inner: Exception) {
                com.axel.nara.utils.CrashLogger.log(context, "NaraWidgetFallback", inner)
            }
        }
    }

    private fun toggleExpandedMode(context: Context, appWidgetId: Int) {
        val current = com.axel.nara.state.WidgetPrefs.isExpandedMode(context)
        com.axel.nara.state.WidgetPrefs.setExpandedMode(context, !current)

        val manager = AppWidgetManager.getInstance(context)
        updateWidget(context, manager, appWidgetId)
    }
}
