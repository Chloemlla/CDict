package com.chloemlla.cdict.core.net

import android.content.Context
import android.util.Log
import com.chloemlla.cdict.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val CDICT_SIGNATURE_VERSION = "1"

object CDictRequestSigner {
    private const val TAG = "CDictRequestSigner"
    private const val PREFS_NAME = "cdict_network"
    private const val INSTALL_ID_KEY = "install_id"
    private val random = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadStarted = AtomicBoolean(false)

    @Volatile
    private var installId: String = ""

    /**
     * 读取（首次生成）安装标识。
     *
     * SharedPreferences 是同步磁盘 I/O，所以整段搬到 IO 线程，调用方不为它等待；就绪之前的
     * 请求走 [sign] 的未签名早退，落在后端的 IP 限流层。
     */
    fun initialize(context: Context) {
        if (!loadStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val stored = prefs.getString(INSTALL_ID_KEY, null)
                installId = stored?.takeIf { it.matches(INSTALL_ID_PATTERN) }
                    ?: UUID.randomUUID().toString().also { prefs.edit().putString(INSTALL_ID_KEY, it).apply() }
            }.onFailure { error ->
                Log.w(TAG, "安装标识读取失败，请求将不带签名", error)
            }
        }
    }

    fun sign(connection: HttpURLConnection, method: String, url: String, body: String = "") {
        val secret = BuildConfig.CDICT_APP_SIGN_SECRET
        val id = installId
        if (secret.isBlank() || id.isBlank()) {
            Log.w(TAG, "未签名：${if (secret.isBlank()) "本次构建未注入应用密钥" else "安装标识尚未就绪"}")
            return
        }

        val parsedUrl = URL(url)
        if (!isBackendUrl(parsedUrl)) {
            Log.w(TAG, "未签名：不是自有后端地址 ${parsedUrl.protocol}://${parsedUrl.host}${parsedUrl.path}")
            return
        }

        val timestamp = System.currentTimeMillis().toString()
        val nonce = ByteArray(16).also(random::nextBytes).toHex()
        val headers = buildCdictSignatureHeaders(
            secret = secret,
            installId = id,
            method = method,
            requestTarget = parsedUrl.file,
            body = body,
            timestamp = timestamp,
            nonce = nonce,
        )
        headers.forEach(connection::setRequestProperty)
    }

    internal fun isBackendUrl(url: URL): Boolean =
        url.protocol == "https" &&
            url.host.equals(URL(CDictBackend.BASE_URL).host, ignoreCase = true) &&
            (url.path == "/api/cdict" || url.path.startsWith("/api/cdict/"))

    private val INSTALL_ID_PATTERN = Regex("^[A-Za-z0-9_-]{8,64}$")
}

internal fun buildCdictSignatureHeaders(
    secret: String,
    installId: String,
    method: String,
    requestTarget: String,
    body: String,
    timestamp: String,
    nonce: String,
): Map<String, String> {
    val canonical = listOf(timestamp, nonce, installId, method.uppercase(), requestTarget, body).joinToString("\n")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mapOf(
        "X-CDict-Sig-Version" to CDICT_SIGNATURE_VERSION,
        "X-CDict-Ts" to timestamp,
        "X-CDict-Nonce" to nonce,
        "X-CDict-Install" to installId,
        "X-CDict-Sig" to mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).toHex(),
    )
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
