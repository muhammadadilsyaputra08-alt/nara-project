package com.axel.nara.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.axel.nara.R
import com.axel.nara.service.AssistantService
import com.axel.nara.state.SessionStore
import com.axel.nara.ui.activity.SettingsActivity

/**
 * Membangun RemoteViews untuk mode compact & expanded, sesuai desain dua mode widget.
 * Dipanggil AssistantWidgetProvider setiap kali widget perlu di-render ulang.
 */
object AssistantWidgetRemoteViews {

    fun build(
        context: Context,
        appWidgetId: Int,
        expanded: Boolean,
        snapshot: SessionStore.SessionSnapshot
    ): RemoteViews {
        val layoutRes = if (expanded) R.layout.widget_expanded else R.layout.widget_compact
        val views = RemoteViews(context.packageName, layoutRes)

        // CATATAN FIX "ikon baru muncul setelah resize manual": beberapa launcher (terutama saat
        // widget baru pertama ditambahkan) tidak selalu menginflate android:src statis dari XML
        // dengan benar pada render pertama — resize memaksa launcher re-request RemoteViews penuh
        // sehingga ikon "muncul". Set ulang src secara programatik di sini supaya payload RemoteViews
        // yang dikirim SELALU eksplisit menyertakan resource ikon, tidak bergantung pada inflate XML.
        views.setImageViewResource(R.id.btn_mic, R.drawable.ic_mic)
        runCatching { views.setImageViewResource(R.id.btn_settings, R.drawable.ic_settings) }
        runCatching { views.setImageViewResource(R.id.img_logo, R.mipmap.ic_launcher_round) }
        runCatching { views.setImageViewResource(R.id.btn_mode_toggle, R.drawable.ic_offline) }
        runCatching { views.setImageViewResource(R.id.btn_retry, R.drawable.ic_retry) }

        views.setOnClickPendingIntent(R.id.btn_mic, micPendingIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.tv_last_response, quickInputPendingIntent(context, appWidgetId))

        if (expanded) {
            views.setOnClickPendingIntent(R.id.btn_settings, settingsPendingIntent(context))
            views.setOnClickPendingIntent(R.id.btn_mode_toggle, toggleModePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.btn_retry, retryPendingIntent(context, appWidgetId))
        } else {
            // Pill compact: settings & logo juga langsung bisa ditekan tanpa perlu masuk mode expanded.
            views.setOnClickPendingIntent(R.id.btn_settings, settingsPendingIntent(context))
            views.setOnClickPendingIntent(R.id.img_logo, settingsPendingIntent(context))
        }

        WidgetStateBinder.bind(context, views, snapshot)
        return views
    }

    private fun micPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, AssistantWidgetProvider::class.java).apply {
            action = AssistantWidgetProvider.ACTION_MIC_TAP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun settingsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SettingsActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun quickInputPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, com.axel.nara.ui.activity.QuickInputActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context, appWidgetId + 300_000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun toggleModePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, AssistantWidgetProvider::class.java).apply {
            action = AssistantWidgetProvider.ACTION_TOGGLE_MODE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context, appWidgetId + 100_000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun retryPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, AssistantWidgetProvider::class.java).apply {
            action = AssistantWidgetProvider.ACTION_RETRY
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context, appWidgetId + 200_000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun startAssistantSession(context: Context) {
        val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!micGranted) {
            android.widget.Toast.makeText(
                context,
                "Izin mikrofon belum diberikan. Buka app Nara sekali untuk memberi izin.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            val openApp = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(openApp)
            return
        }

        val serviceIntent = Intent(context, AssistantService::class.java).apply {
            action = AssistantService.ACTION_START_SESSION
        }
        context.startForegroundService(serviceIntent)
    }
}
