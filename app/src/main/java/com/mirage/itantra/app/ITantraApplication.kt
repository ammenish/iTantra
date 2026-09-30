package com.mirage.itantra.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * iTantra application entry point.
 *
 * @HiltAndroidApp triggers Hilt's code generation for
 * dependency injection across the application.
 */
@HiltAndroidApp
class ITantraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Intercept fatal crashes and display them on screen
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val stackTrace = android.util.Log.getStackTraceString(e)
            val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                putExtra("crash_log", stackTrace)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(1)
        }
    }
}
