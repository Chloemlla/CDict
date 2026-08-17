package com.chloemlla.cdict

import android.app.Application
import android.util.Log
import com.chloemlla.lumen.crash.LumenCrash

/**
 * Application entry point.
 *
 * Installs the Lumen Crash SDK in [onCreate] so [android.content.Context.getApplicationContext]
 * is available (it returns null during [android.app.Application.attachBaseContext]).
 * A failed initialization is logged but never blocks startup.
 */
class CDictApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashSdk()
    }

    private fun installCrashSdk() {
        runCatching {
            LumenCrash.install(this) {
                appDisplayName = "CDict"
                versionName = BuildConfig.VERSION_NAME
                versionCode = BuildConfig.VERSION_CODE
                crashReportBackendEnabled = true
                onCrashSaved = { /* host-side report upload hook */ }
            }
        }.onFailure { error ->
            Log.e(TAG, "Lumen Crash SDK installation failed", error)
        }
    }

    private companion object {
        const val TAG = "CDictApplication"
    }
}