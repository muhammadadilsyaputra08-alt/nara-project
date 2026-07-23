package com.axel.nara.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utilitas jaringan — dipakai ActionRouter untuk memutuskan apakah aksi dengan
 * connection_required = "internet" bisa dijalankan sekarang.
 */
object NetworkUtils {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
