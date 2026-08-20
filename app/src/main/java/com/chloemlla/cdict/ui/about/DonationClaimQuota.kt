package com.chloemlla.cdict.ui.about

/**
 * 署名提交的本地限流状态：两次提交至少间隔 [MIN_INTERVAL_MILLIS]，同一小时窗口内最多 [MAX_PER_WINDOW] 次。
 *
 * 后端另有每 IP 每小时 10 次的限流，本地这一层只为挡住误触连点，不让必然被拒的请求发出去。
 * 状态存在 [AboutStore] 的 prefs 里，纯本地判断，不联网也不上报。
 */
data class DonationClaimQuota(
    val windowStart: Long,
    val count: Int,
    val lastSubmitMillis: Long,
) {
    /** 允许提交时返回 null，否则返回可以直接给用户看的原因。 */
    fun rejectionAt(now: Long): String? {
        val sinceLast = now - lastSubmitMillis
        if (lastSubmitMillis in 1..now && sinceLast < MIN_INTERVAL_MILLIS) {
            val wait = (MIN_INTERVAL_MILLIS - sinceLast + 999) / 1000
            return "提交太频繁了，请 $wait 秒后再试"
        }
        if (inWindow(now) && count >= MAX_PER_WINDOW) {
            val wait = (windowStart + WINDOW_MILLIS - now) / 60_000 + 1
            return "一小时内最多提交 $MAX_PER_WINDOW 次，请 $wait 分钟后再试"
        }
        return null
    }

    /** 记一次已发出的提交；窗口过期（或系统时间回拨）时重新开窗。 */
    fun accepted(now: Long): DonationClaimQuota =
        if (inWindow(now)) {
            copy(count = count + 1, lastSubmitMillis = now)
        } else {
            DonationClaimQuota(windowStart = now, count = 1, lastSubmitMillis = now)
        }

    private fun inWindow(now: Long): Boolean = now - windowStart in 0 until WINDOW_MILLIS

    companion object {
        const val MIN_INTERVAL_MILLIS = 30_000L
        const val WINDOW_MILLIS = 60 * 60 * 1000L
        const val MAX_PER_WINDOW = 5

        val EMPTY = DonationClaimQuota(windowStart = 0L, count = 0, lastSubmitMillis = 0L)
    }
}
