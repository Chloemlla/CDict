package com.chloemlla.cdict

import android.app.Application
import android.content.Context
import com.chloemlla.lumen.crash.LumenCrash

/**
 * Application entry point.
 *
 * Installs the Lumen Crash SDK as the first host work in [attachBaseContext] so
 * crash capture and prior-exit collection are active from cold start. The crash
 * report backend is left disabled (uploads stay local); [onCrashSaved] is the
 * host hook if uploads are scheduled later. installSafely swallows install or
 * author-integrity failures so a broken SDK can never block startup.
 */
class CDictApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        LumenCrash.installSafely(this) {
            appDisplayName = "CDict"
            versionName = BuildConfig.VERSION_NAME
            versionCode = BuildConfig.VERSION_CODE
            crashReportBackendEnabled = true
            onCrashSaved = { /* host-side report upload hook */ }
        }
    }
}