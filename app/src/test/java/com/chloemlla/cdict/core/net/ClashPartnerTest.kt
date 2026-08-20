package com.chloemlla.cdict.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClashPartnerTest {
    @Test
    fun parseClashStatus_returnsNullForEmptyBundleValues() {
        assertNull(parseClashStatus(emptyMap()))
    }

    @Test
    fun parseClashStatus_prefersVpnStateOverLegacyVpnRunning() {
        val connecting = parseClashStatus(
            mapOf("apiVersion" to 2, "running" to true, "vpnRunning" to true, "vpnState" to 1),
        )

        assertTrue(connecting?.coreRunning == true)
        assertFalse(connecting?.vpnConnected == true)
    }

    @Test
    fun parseClashStatus_fallsBackToVpnRunningWhenApiV1() {
        val status = parseClashStatus(mapOf("apiVersion" to 1, "running" to true, "vpnRunning" to true))

        assertTrue(status?.vpnConnected == true)
    }

    @Test
    fun parseClashStatus_acceptsLegacyAutoAdaptAlias() {
        val status = parseClashStatus(mapOf("piliPlusAutoAdapt" to true, "name" to "home"))

        assertTrue(status?.partnerAutoAdapt == true)
        assertEquals("home", status?.profileName)
    }

    @Test
    fun resolveClashRoute_usesLocalProxyOnlyWhenCoreRunsOutsideTunnel() {
        val running = status(coreRunning = true, vpnConnected = false)

        assertEquals(
            ClashRoute.LocalProxy,
            resolveClashRoute(running, adaptEnabled = true, localProxyReachable = true),
        )
    }

    @Test
    fun resolveClashRoute_staysDirectWhenTunnelAlreadyCarriesTraffic() {
        val tunneled = status(coreRunning = true, vpnConnected = true)

        assertEquals(
            ClashRoute.Direct,
            resolveClashRoute(tunneled, adaptEnabled = true, localProxyReachable = true),
        )
    }

    @Test
    fun resolveClashRoute_staysDirectWhenDisabledOrUnavailable() {
        val running = status(coreRunning = true, vpnConnected = false)

        assertEquals(
            ClashRoute.Direct,
            resolveClashRoute(running, adaptEnabled = false, localProxyReachable = true),
        )
        assertEquals(
            ClashRoute.Direct,
            resolveClashRoute(running, adaptEnabled = true, localProxyReachable = false),
        )
        assertEquals(
            ClashRoute.Direct,
            resolveClashRoute(null, adaptEnabled = true, localProxyReachable = true),
        )
        assertEquals(
            ClashRoute.Direct,
            resolveClashRoute(
                status(coreRunning = false, vpnConnected = false),
                adaptEnabled = true,
                localProxyReachable = true,
            ),
        )
    }

    @Test
    fun summary_describesNotInstalledAndDisabledAndProxiedStates() {
        assertTrue(ClashPartnerState().summary().contains("未检测到"))
        assertTrue(
            ClashPartnerState(installedPackage = PACKAGE, adaptEnabled = false)
                .summary()
                .contains("已关闭跟随"),
        )
        assertTrue(
            ClashPartnerState(installedPackage = PACKAGE, statusReadable = false)
                .summary()
                .contains("读不到伙伴状态"),
        )
        assertTrue(
            ClashPartnerState(
                installedPackage = PACKAGE,
                statusReadable = true,
                coreRunning = true,
                route = ClashRoute.LocalProxy,
            ).summary().contains("${ClashPartner.LOCAL_PROXY_HOST}:${ClashPartner.LOCAL_PROXY_PORT}"),
        )
    }

    @Test
    fun summary_hintsAtAutoAdaptWhenTunnelUpButPartnerIncludeOff() {
        val summary = ClashPartnerState(
            installedPackage = PACKAGE,
            statusReadable = true,
            coreRunning = true,
            vpnConnected = true,
            partnerAutoAdapt = false,
            profileName = "home",
        ).summary()

        assertTrue(summary.contains("隧道已连接"))
        assertTrue(summary.contains("伙伴应用自动适配"))
        assertTrue(summary.contains("home"))
    }

    private fun status(coreRunning: Boolean, vpnConnected: Boolean) = ClashStatus(
        coreRunning = coreRunning,
        vpnConnected = vpnConnected,
        partnerAutoAdapt = true,
        profileName = null,
    )

    private companion object {
        const val PACKAGE = "com.github.metacubex.clash"
    }
}
