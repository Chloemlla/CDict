package com.chloemlla.cdict.core.net

import android.app.Activity
import android.content.Context
import android.content.Intent
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

/**
 * CMFA 授予 CDict 的读取层级，对应 `partnerStatus` 的 `accessTier` 字段。
 *
 * [Basic] 是按敏感度分层的低敏感层：只有内核/隧道状态与自动适配开关，足够决定出口，但读不到
 * 配置名、节点、流量与错误信息。
 */
enum class ClashAccess { Unavailable, Denied, Basic, Full }

/** 伙伴应用（Clash Meta for Android）的只读快照，供关于页展示与路由决策使用。 */
data class ClashPartnerState(
    val installedPackage: String? = null,
    val access: ClashAccess = ClashAccess.Unavailable,
    val deniedReason: String? = null,
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
 * 读出 CMFA 授予的层级。
 *
 * apiVersion 3 起 `accessTier` 明确回传 `denied` / `basic` / `full`；更早的 CMFA 不带这个字段，
 * 但那时只要能读到内容就等于拿到了全部字段，所以按 [ClashAccess.Full] 处理。
 */
internal fun parseClashAccess(values: Map<String, Any?>): ClashAccess =
    when (values["accessTier"] as? String) {
        "denied" -> ClashAccess.Denied
        "basic" -> ClashAccess.Basic
        "full" -> ClashAccess.Full
        else -> if (values.isEmpty()) ClashAccess.Unavailable else ClashAccess.Full
    }

/**
 * 把 CMFA 的机器可读 `deniedReason` 翻成用户能照着做的一句中文。
 *
 * 这些取值来自 CMFA 的 `PartnerAccessResolver`；未知取值原样带出，便于对着 logcat 排查。
 */
internal fun describeDeniedReason(reason: String?): String = when (reason) {
    "pending_user_approval" -> "等待在 Clash 中确认配对：打开 Clash 主页或点击配对通知即可授权"
    "denied_by_user" -> "已在 Clash 中拒绝授权，可在 Clash 主页「伙伴应用」里撤销"
    "signer_unverified" -> "Clash 未登记 CDict 的签名证书，只开放基础状态；在「伙伴应用」里允许即可读取完整状态"
    "not_partner" -> "Clash 没把 CDict 认成伙伴应用，请更新 Clash 到支持伙伴配对的版本"
    "no_signature" -> "Clash 读不到 CDict 的签名信息，无法完成配对"
    null -> "Clash 未说明原因"
    else -> "Clash 返回原因：$reason"
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
fun ClashPartnerState.summary(): String {
    val base = when {
        installedPackage == null -> "未安装 Clash Meta for Android（可选），联网请求直连"
        !adaptEnabled -> "已关闭跟随，联网请求直连"
        access == ClashAccess.Unavailable ->
            "已安装，但这个 Clash 版本没有伙伴状态接口，请更新 Clash 后重试"
        access == ClashAccess.Denied -> "读不到伙伴状态：${describeDeniedReason(deniedReason)}"
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

    // 基础层能决定出口，但配置名/节点/流量都读不到，所以要顺带说明缺什么、怎么补。
    return if (access == ClashAccess.Basic) "$base · ${describeDeniedReason(deniedReason)}" else base
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

    /** CMFA 导出的伙伴配对确认窗：类名固定在 com.github.kr328.clash 命名空间，applicationId 随 flavor 变化。 */
    private const val PARTNER_PAIRING_ACTIVITY = "com.github.kr328.clash.PartnerPairingActivity"

    /** 配对确认窗的请求码：只为让平台填上调用方身份，回传结果不使用。 */
    private const val REQUEST_PAIRING = 0x0C1A

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

    /** 一次 `partnerStatus` 查询的结果：层级、拒绝原因，以及真正读到的字段。 */
    private data class PartnerRead(
        val access: ClashAccess,
        val deniedReason: String?,
        val status: ClashStatus?,
    )

    private val UNAVAILABLE = PartnerRead(ClashAccess.Unavailable, null, null)

    private val _state = MutableStateFlow(ClashPartnerState())
    val state: StateFlow<ClashPartnerState> = _state.asStateFlow()

    private val started = AtomicBoolean(false)
    /** 每进程只发起一次配对确认；CMFA 侧在用户已作答时会静默关闭。 */
    private val pairingRequested = AtomicBoolean(false)
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

    /**
     * 拉起 CMFA 的伙伴配对确认窗。
     *
     * 后台应用不能替别人弹窗（BAL 拦截，CMFA 因此退化成通知）；CMFA 把配对窗导出后，由前台
     * 伙伴应用自己发起，透明确认窗就能盖在本应用之上。CMFA 只认平台回传的发起者身份，
     * `startActivityForResult` 才会填上 `getCallingPackage()`（API 34 以下唯一不可伪造的来源），
     * 所以这里不能用 application context + NEW_TASK 启动。结果本身不需要，也不必处理回调。
     * CMFA 在用户已作答时会静默关闭，因此每进程只发起一次。只在用户可见的前台入口调用。
     */
    fun requestPairing(activity: Activity) {
        if (!pairingRequested.compareAndSet(false, true)) return
        val clashPackage = detectClashPackage(activity.applicationContext) ?: return
        val intent = Intent().setClassName(clashPackage, PARTNER_PAIRING_ACTIVITY)
        runCatching { activity.startActivityForResult(intent, REQUEST_PAIRING) }
            .onFailure { error -> Log.w(TAG, "拉起 Clash 配对确认失败：$clashPackage", error) }
    }

    private fun refreshNow() {
        val context = appContext ?: return
        val installed = detectClashPackage(context)
        val read = installed?.let { queryPartnerStatus(context, it) } ?: UNAVAILABLE
        val status = read.status
        if (status != null) {
            localProxyReachable = true
        }
        val resolved = resolveClashRoute(status, adaptEnabled, localProxyReachable)
        route = resolved
        _state.value = ClashPartnerState(
            installedPackage = installed,
            access = read.access,
            deniedReason = read.deniedReason,
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

    /**
     * 查一次 `partnerStatus`。
     *
     * 三种失败要分开：Provider 缺失或 binder 异常（旧版 Clash，没有伙伴接口）、CMFA 明确拒绝
     * （带 `deniedReason`，用户照着做就能解决）、以及只授予基础层。混成一句「读不到状态」时
     * 用户无从下手，这也是先前那条无用提示的根因。
     */
    private fun queryPartnerStatus(context: Context, packageName: String): PartnerRead {
        val uri = Uri.parse("content://$packageName$STATUS_AUTHORITY_SUFFIX")
        val bundle = try {
            context.contentResolver.call(uri, STATUS_METHOD, null, null)
        } catch (t: Throwable) {
            Log.d(TAG, "partnerStatus 查询失败：$packageName", t)
            null
        } ?: return UNAVAILABLE
        val values = bundle.toValueMap()
        val access = parseClashAccess(values)
        val reason = values["deniedReason"] as? String
        if (access != ClashAccess.Full) {
            Log.d(TAG, "伙伴状态受限：$packageName tier=$access reason=$reason")
        }
        return PartnerRead(
            access = access,
            deniedReason = reason,
            status = parseClashStatus(values).takeIf { access != ClashAccess.Denied },
        )
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
