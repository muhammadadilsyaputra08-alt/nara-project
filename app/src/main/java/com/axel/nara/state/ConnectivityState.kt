package com.axel.nara.state

import android.content.Context
import com.axel.nara.utils.NetworkUtils

/**
 * Sumber kebenaran tunggal untuk status konektivitas saat ini, dipakai ActionRouter
 * untuk memutuskan apakah aksi dengan connection_required="internet" bisa dijalankan.
 */
class ConnectivityState(private val appContext: Context) {

    enum class Mode { ONLINE, OFFLINE, LOCAL_ONLY }

    fun currentMode(): Mode =
        if (NetworkUtils.isOnline(appContext)) Mode.ONLINE else Mode.OFFLINE

    fun canRun(requirement: com.axel.nara.model.ConnectionRequirement): Boolean = when (requirement) {
        com.axel.nara.model.ConnectionRequirement.internet -> currentMode() == Mode.ONLINE
        com.axel.nara.model.ConnectionRequirement.local -> true
        com.axel.nara.model.ConnectionRequirement.none -> true
    }
}
