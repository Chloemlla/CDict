package com.chloemlla.cdict.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DonationClaimQuotaTest {

    @Test
    fun `first submission is always allowed`() {
        assertNull(DonationClaimQuota.EMPTY.rejectionAt(1_000L))
    }

    @Test
    fun `second submission within the cooldown is blocked`() {
        val quota = DonationClaimQuota.EMPTY.accepted(100_000L)
        assertNotNull(quota.rejectionAt(105_000L))
        assertNull(quota.rejectionAt(100_000L + DonationClaimQuota.MIN_INTERVAL_MILLIS))
    }

    @Test
    fun `window cap blocks further submissions until the window rolls over`() {
        var now = 0L
        var quota = DonationClaimQuota.EMPTY
        repeat(DonationClaimQuota.MAX_PER_WINDOW) {
            assertNull(quota.rejectionAt(now))
            quota = quota.accepted(now)
            now += DonationClaimQuota.MIN_INTERVAL_MILLIS
        }
        assertNotNull(quota.rejectionAt(now))
        assertNull(quota.rejectionAt(DonationClaimQuota.WINDOW_MILLIS + 1L))
    }

    @Test
    fun `clock moving backwards restarts the window instead of locking the form`() {
        val quota = DonationClaimQuota(windowStart = 10_000_000L, count = 99, lastSubmitMillis = 10_000_000L)
        assertNull(quota.rejectionAt(1_000L))
        assertEquals(1, quota.accepted(1_000L).count)
    }
}
