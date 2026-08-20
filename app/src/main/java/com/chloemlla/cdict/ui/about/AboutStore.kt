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

    /** 朗读来源优先级：true=词典静态音频优先，false=在线合成优先。默认前者。 */
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

    /** 累计前台使用时长（毫秒），由 MainActivity 的 onResume/onPause 累加，仅本地判断赞赏提示时机。 */
    var foregroundMillis: Long
        get() = prefs.getLong("foreground_millis", 0L)
        set(value) {
            prefs.edit { putLong("foreground_millis", value) }
        }

    /** 赞赏提示是否已经自动出现过一次。 */
    var tipPromptShown: Boolean
        get() = prefs.getBoolean("tip_prompt_shown", false)
        set(value) {
            prefs.edit { putBoolean("tip_prompt_shown", value) }
        }

    /** 赞赏提示是否已被用户关掉；一旦为 true 就永不再自动出现。 */
    var tipPromptDismissed: Boolean
        get() = prefs.getBoolean("tip_prompt_dismissed", false)
        set(value) {
            prefs.edit { putBoolean("tip_prompt_dismissed", value) }
        }

    /** 是否至少完整做完过一轮复习；与 [foregroundMillis] 共同构成赞赏提示的出现条件。 */
    var reviewRoundDone: Boolean
        get() = prefs.getBoolean("review_round_done", false)
        set(value) {
            prefs.edit { putBoolean("review_round_done", value) }
        }
}
