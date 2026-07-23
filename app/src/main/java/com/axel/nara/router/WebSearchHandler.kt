package com.axel.nara.router

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.axel.nara.model.IntentResult

/**
 * Eksekusi untuk category = web_search.
 * Dipanggil ActionRouter HANYA setelah dipastikan online (lihat ConnectivityState).
 */
class WebSearchHandler(private val context: Context) {

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    fun handle(intent: IntentResult): Result {
        val query = intent.payloadString("query")
        if (query.isNullOrBlank()) {
            return Result.Failure("Query pencarian kosong.")
        }

        return when (intent.target) {
            "google" -> runGoogleSearch(query)
            "browser_search" -> runBrowserSearch(query)
            else -> runGoogleSearch(query) // default aman
        }
    }

    private fun runGoogleSearch(query: String): Result {
        val webIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (webIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(webIntent)
            Result.Success("Mencari: $query")
        } else {
            runBrowserSearch(query)
        }
    }

    private fun runBrowserSearch(query: String): Result {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Result.Success("Mencari di browser: $query")
    }
}
