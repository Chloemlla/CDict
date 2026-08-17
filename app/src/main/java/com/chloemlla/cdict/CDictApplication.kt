package com.chloemlla.cdict

import android.app.Application
import android.content.Context
import android.util.Log
import com.chloemlla.lumen.crash.LumenCrash

/**
 * Application entry point.
 *
 * Installs the Lumen Crash SDK as the first host work in [attachBaseContext] so
 * crash capture and prior-exit collection are active from cold start. A failed
 * initialization is logged and retried in [onCreate] without blocking startup.
 */
class CDictApplication : Application() {
    private var crashSdkInstalled = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        crashSdkInstalled = installCrashSdk()
    }

    override fun onCreate() {
        super.onCreate()
        if (!crashSdkInstalled && !LumenCrash.isInstalled()) {
            crashSdkInstalled = installCrashSdk()
        }
    }

    private fun installCrashSdk(): Boolean =
        runCatching {
            LumenCrash.install(this) {
                appDisplayName = "CDict"
                versionName = BuildConfig.VERSION_NAME
                versionCode = BuildConfig.VERSION_CODE
                crashReportBackendEnabled = true
                onCrashSaved = { /* host-side report upload hook */ }
            }
            true
        }.onFailure { error ->
            Log.e(TAG, "Lumen Crash SDK installation failed", error)
        }.getOrDefault(false)

    private companion object {
        const val TAG = "CDictApplication"
    }
}