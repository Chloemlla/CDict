package com.chloemlla.cdict.ui.about

import android.content.Context
import androidx.core.content.edit

class AboutStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("cdict_about", Context.MODE_PRIVATE)

    var ossNoticeSeen: Boolean
        get() = prefs.getBoolean("oss_notice_seen", false)
        set(value) {
            prefs.edit { putBoolean("oss_notice_seen", value) }
        }

    var acknowledgedCommitHash: String
        get() = prefs.getString("ack_commit_hash", "") ?: ""
        set(value) {
            prefs.edit { putString("ack_commit_hash", value) }
        }

    var acknowledgedBuildTime: Long
        get() = prefs.getLong("ack_build_time", 0L)
        set(value) {
            prefs.edit { putLong("ack_build_time", value) }
        }

    /** 朗读来源优先级：true=有道优先，false=vivo 优先。默认有道优先。 */
    var youdaoFirst: Boolean
        get() = prefs.getBoolean("youdao_first", true)
        set(value) {
            prefs.edit { putBoolean("youdao_first", value) }
        }

    /** 启动时是否自动检查软件更新。默认开启。 */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean("auto_check_update", true)
        set(value) {
            prefs.edit { putBoolean("auto_check_update", value) }
        }

    /** 检测到伙伴应用 Clash 时，是否让联网请求跟随其本地代理。默认开启。 */
    var clashProxyAdapt: Boolean
        get() = prefs.getBoolean("clash_proxy_adapt", true)
        set(value) {
            prefs.edit { putBoolean("clash_proxy_adapt", value) }
        }
}
