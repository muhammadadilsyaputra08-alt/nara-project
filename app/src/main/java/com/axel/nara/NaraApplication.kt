package com.axel.nara

import android.app.Application
import com.axel.nara.utils.CrashLogger

class NaraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLogger.log(applicationContext, "NaraCrash", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
