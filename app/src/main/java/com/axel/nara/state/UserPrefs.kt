package com.axel.nara.state

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nara_user_prefs")

/**
 * Preferensi ringan pengguna (widget mode, prioritas offline, dsb).
 * Disimpan via Jetpack DataStore sesuai rekomendasi teknis desain (bukan SharedPreferences penuh).
 */
class UserPrefs(private val context: Context) {

    private object Keys {
        val EXPANDED_MODE = booleanPreferencesKey("expanded_mode")
        val PREFER_OFFLINE = booleanPreferencesKey("prefer_offline")
    }

    val isExpandedMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.EXPANDED_MODE] ?: false }

    val preferOffline: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PREFER_OFFLINE] ?: false }

    suspend fun setExpandedMode(value: Boolean) {
        context.dataStore.edit { it[Keys.EXPANDED_MODE] = value }
    }

    suspend fun setPreferOffline(value: Boolean) {
        context.dataStore.edit { it[Keys.PREFER_OFFLINE] = value }
    }
}
