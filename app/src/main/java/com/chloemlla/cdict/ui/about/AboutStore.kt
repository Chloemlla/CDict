package com.chloemlla.cdict.ui.about

import android.content.Context

class AboutStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("cdict_about", Context.MODE_PRIVATE)

    var ossNoticeSeen: Boolean
        get() = prefs.getBoolean("oss_notice_seen", false)
        set(value) {
            prefs.edit().putBoolean("oss_notice_seen", value).apply()
        }

    var acknowledgedCommitHash: String
        get() = prefs.getString("ack_commit_hash", "") ?: ""
        set(value) {
            prefs.edit().putString("ack_commit_hash", value).apply()
        }

    var acknowledgedBuildTime: Long
        get() = prefs.getLong("ack_build_time", 0L)
        set(value) {
            prefs.edit().putLong("ack_build_time", value).apply()
        }
}
