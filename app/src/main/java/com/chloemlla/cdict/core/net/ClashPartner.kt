package com.chloemlla.cdict.core.net

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** CDict 联网请求的出口选择。 */
enum class ClashRoute { Direct, LocalProxy }

/** 伙伴应用（Clash Meta for Android）的只读快照，供关于页展示与路由决策使用。 */
data class ClashPartnerState(
    val installedPackage: String? = null,
    val statusReadable: Boolean = false,
    val coreRunning: Boolean = false,
    val vpnConnected: Boolean = false,
    val partnerAutoAdapt: Boolean = false,
    val profileName: String? = null,
    val adaptEnabled: Boolean = true,
    val route: ClashRoute = ClashRoute.Direct,
)

/** `partnerStatus` 返回值中 CDict 用到的字段。 */
internal data class ClashStatus(
    val coreRunning: Boolean,
    val vpnConnected: Boolean,
    val partnerAutoAdapt: Boolean,
    val profileName: String?,
)

/**
 * 把 `partnerStatus` 的键值对收敛成 [ClashStatus]。
 *
 * apiVersion 2 起用 `vpnState`（0 断开 / 1 连接中 / 2 已连接）表达隧道状态，v1 只有布尔
 * `vpnRunning`；`piliPlusAutoAdapt` 是 `partnerAppAutoAdapt` 的旧别名。
 */
internal fun parseClashStatus(values: Map<String, Any?>): ClashStatus? {
    if (values.isEmpty()) return null
    val vpnState = values["vpnState"] as? Int
    return ClashStatus(
        coreRunning = values["running"] as? Boolean ?: false,
        vpnConnected = if (vpnState != null) {
            vpnState == ClashPartner.VPN_STATE_CONNECTED
        } else {
            values["vpnRunning"] as? Boolean ?: false
        },
        partnerAutoAdapt = values["partnerAppAutoAdapt"] as? Boolean
            ?: values["piliPlusAutoAdapt"] as? Boolean
            ?: false,
        profileName = values["name"] as? String,
    )
}

/**
 * 决定这一刻的出口：只在「内核在跑、隧道没接管本进程」时才借用 Clash 的本地混合端口。
 *
 * 隧道已连接时必须直连——流量已经由 VPN 承载，再叠一层本地代理会绕回隧道自身。内核没在跑
 * 或本地端口连不上时同样直连，避免把 CDict 的在线翻译/朗读全部打死。
 */
internal fun resolveClashRoute(
    status: ClashStatus?,
    adaptEnabled: Boolean,
    localProxyReachable: Boolean,
): ClashRoute = when {
    !adaptEnabled || status == null -> ClashRoute.Direct
    status.vpnConnected -> ClashRoute.Direct
    status.coreRunning && localProxyReachable -> ClashRoute.LocalProxy
    else -> ClashRoute.Direct
}

/** 关于页「伙伴应用」分区的一行状态文案。 */
fun ClashPartnerState.summary(): String = when {
    installedPackage == null -> "未安装 Clash Meta for Android（可选），联网请求直连"
    !adaptEnabled -> "已关闭跟随，联网请求直连"
    !statusReadable -> "已安装，但读不到伙伴状态（需同签名或已登记的伙伴身份）"
    vpnConnected -> buildString {
        append("隧道已连接，CDict 流量随 VPN 走 Clash")
        if (!partnerAutoAdapt) append(" · 建议在 Clash 中开启伙伴应用自动适配")
        profileName?.let { append(" · 配置：$it") }
    }
    route == ClashRoute.LocalProxy ->
        "内核运行中，联网请求走本地混合端口 ${ClashPartner.LOCAL_PROXY_HOST}:${ClashPartner.LOCAL_PROXY_PORT}"
    coreRunning -> "内核运行中，但本地混合端口连接失败，已回退直连"
    else -> "Clash 已安装但内核未运行，联网请求直连"
}

/**
 * CDict 与 Clash Meta for Android 的伙伴适配桥。
 *
 * CDict 在 `AndroidManifest.xml` 里声明 `com.github.kr328.clash.partner` 标记，由 CMFA 的
 * `PartnerApps` 注册表识别后自动纳入 VPN 访问控制；本类反向只做两件事：读取 CMFA 导出的
 * 只读 `partnerStatus`，以及据此把在线翻译/朗读/更新检查的请求送到正确的出口。
 *
 * 接口是**单向只读**的：这里只 query 状态，永远不请求启动/停止/切换 VPN。
 */
object ClashPartner {
    const val LOCAL_PROXY_HOST = "127.0.0.1"
    const val LOCAL_PROXY_PORT = 7890
    internal const val VPN_STATE_CONNECTED = 2

    /** 已知的 CMFA 应用 ID，按优先级排列（Meta 正式版 → Alpha → 旧 Meta → 上游 kr328）。 */
    val knownPackages: List<String> = listOf(
        "com.github.metacubex.clash",
        "com.github.metacubex.clash.alpha",
        "com.github.metacubex.clash.meta",
        "com.github.kr328.clash",
    )

    private const val TAG = "ClashPartner"
    private const val STATUS_AUTHORITY_SUFFIX = ".status"
    private const val STATUS_METHOD = "partnerStatus"

    private val _state = MutableStateFlow(ClashPartnerState())
    val state: StateFlow<ClashPartnerState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 字面量 IP，构造时不会触发 DNS 解析；解析后的地址才能与 connectFailed 回传的地址相等。
    private val localProxyAddress = InetSocketAddress(LOCAL_PROXY_HOST, LOCAL_PROXY_PORT)

    @Volatile
    private var route: ClashRoute = ClashRoute.Direct

    @Volatile
    private var localProxyReachable: Boolean = true

    @Volatile
    private var adaptEnabled: Boolean = true

    @Volatile
    private var appContext: Context? = null

    /**
     * 安装全局 [ProxySelector] 并开始跟踪 Clash 状态。
     *
     * [adaptEnabledProvider] 在 IO 线程调用，避免启动时在主线程读取 SharedPreferences。
     * 首次刷新完成前出口保持直连。
     */
    fun start(context: Context, adaptEnabledProvider: () -> Boolean) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        installProxySelector()
        watchVpnNetwork()
        scope.launch {
            adaptEnabled = runCatching(adaptEnabledProvider).getOrDefault(true)
            refreshNow()
        }
    }

    /** 用户在关于页切换「跟随 Clash 代理」后调用。 */
    fun setAdaptEnabled(enabled: Boolean) {
        adaptEnabled = enabled
        refresh()
    }

    /** 重新检测安装状态并拉取一次 `partnerStatus`。 */
    fun refresh() {
        if (appContext == null) return
        scope.launch { refreshNow() }
    }

    private fun refreshNow() {
        val context = appContext ?: return
        val installed = detectClashPackage(context)
        val status = installed?.let { queryPartnerStatus(context, it) }
        if (status != null) {
            localProxyReachable = true
        }
        val resolved = resolveClashRoute(status, adaptEnabled, localProxyReachable)
        route = resolved
        _state.value = ClashPartnerState(
            installedPackage = installed,
            statusReadable = status != null,
            coreRunning = status?.coreRunning ?: false,
            vpnConnected = status?.vpnConnected ?: false,
            partnerAutoAdapt = status?.partnerAutoAdapt ?: false,
            profileName = status?.profileName,
            adaptEnabled = adaptEnabled,
            route = resolved,
        )
    }

    private fun detectClashPackage(context: Context): String? {
        val pm = context.packageManager
        return knownPackages.firstOrNull { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    private fun queryPartnerStatus(context: Context, packageName: String): ClashStatus? {
        val uri = Uri.parse("content://$packageName$STATUS_AUTHORITY_SUFFIX")
        val bundle = try {
            context.contentResolver.call(uri, STATUS_METHOD, null, null)
        } catch (t: Throwable) {
            // 未授予伙伴身份、Provider 缺失或 binder 异常都只意味着「读不到状态」，不能影响联网。
            Log.d(TAG, "partnerStatus 查询失败：$packageName", t)
            null
        } ?: return null
        return parseClashStatus(bundle.toValueMap())
    }

    @Suppress("DEPRECATION")
    private fun Bundle.toValueMap(): Map<String, Any?> = keySet().associateWith { get(it) }

    private fun installProxySelector() {
        val fallback = runCatching { ProxySelector.getDefault() }.getOrNull()
        runCatching {
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    if (route == ClashRoute.LocalProxy) {
                        return listOf(Proxy(Proxy.Type.HTTP, localProxyAddress))
                    }
                    if (uri == null || fallback == null) return listOf(Proxy.NO_PROXY)
                    return runCatching { fallback.select(uri) }.getOrNull() ?: listOf(Proxy.NO_PROXY)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, e: IOException?) {
                    if (sa == localProxyAddress) {
                        // 本地混合端口连不上：立即回退直连，等下一次 refresh 再试。
                        localProxyReachable = false
                        route = ClashRoute.Direct
                        _state.update { it.copy(route = ClashRoute.Direct) }
                        Log.d(TAG, "Clash 本地混合端口不可用，已回退直连", e)
                        return
                    }
                    if (uri != null && fallback != null) {
                        runCatching { fallback.connectFailed(uri, sa, e) }
                    }
                }
            })
        }.onFailure { error ->
            Log.w(TAG, "全局 ProxySelector 安装失败，保持直连", error)
        }
    }

    private fun watchVpnNetwork() {
        val context = appContext ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            // Builder 默认带上 NET_CAPABILITY_NOT_VPN，不移除的话这条 VPN 请求永远匹配不到网络。
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure { error -> Log.w(TAG, "VPN 网络回调注册失败，仅在打开关于页时刷新状态", error) }
    }
}
