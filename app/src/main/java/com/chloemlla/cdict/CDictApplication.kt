package com.chloemlla.cdict

import android.app.Application
import android.util.Log
import com.chloemlla.cdict.core.net.CDictRequestSigner
import com.chloemlla.cdict.core.net.ClashPartner
import com.chloemlla.cdict.ui.about.AboutStore
import com.chloemlla.lumen.crash.LumenCrash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Installs the Lumen Crash SDK in [onCreate] so [android.content.Context.getApplicationContext]
 * is available (it returns null during [android.app.Application.attachBaseContext]).
 * A failed initialization is logged but never blocks startup.
 */
class CDictApplication : Application() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashSdk()
        warmUpPreferences()
        ClashPartner.start(this) { AboutStore(this).clashProxyAdapt }
    }

    /**
     * 冷启动关键路径上不做主线程磁盘读：安装标识与关于页设置都在 IO 线程首次加载，主线程之后
     * 访问命中已加载的 SharedPreferences 实例（签名侧容忍安装标识尚未就绪的短窗口）。
     */
    private fun warmUpPreferences() {
        startupScope.launch {
            CDictRequestSigner.initialize(this@CDictApplication)
            AboutStore.preload(this@CDictApplication)
        }
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