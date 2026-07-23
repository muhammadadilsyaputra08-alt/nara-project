package com.axel.nara.state

import android.content.Context

/**
 * Preferensi widget yang dibaca SINKRON, tanpa coroutine/DataStore.
 * Widget provider berjalan di thread utama saat pertama kali ditambahkan,
 * jadi kita hindari runBlocking+DataStore di sini (berisiko macet/hang
 * tanpa exception, yang bikin launcher menampilkan "Problem loading widget").
 */
object WidgetPrefs {
    private const val PREFS_NAME = "nara_widget_prefs_sync"
    private const val KEY_EXPANDED = "expanded_mode"

    fun isExpandedMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EXPANDED, false)
    }

    fun setExpandedMode(context: Context, value: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EXPANDED, value).apply()
    }
}
