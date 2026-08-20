package com.chloemlla.cdict.core.net

import android.content.Context
import com.chloemlla.cdict.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val CDICT_SIGNATURE_VERSION = "1"

object CDictRequestSigner {
    private const val PREFS_NAME = "cdict_network"
    private const val INSTALL_ID_KEY = "install_id"
    private val random = SecureRandom()

    @Volatile
    private var installId: String = ""

    fun initialize(context: Context) {
        if (installId.isNotEmpty()) return
        synchronized(this) {
            if (installId.isNotEmpty()) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(INSTALL_ID_KEY, null)
            installId = stored?.takeIf { it.matches(INSTALL_ID_PATTERN) }
                ?: UUID.randomUUID().toString().also { prefs.edit().putString(INSTALL_ID_KEY, it).apply() }
        }
    }

    fun sign(connection: HttpURLConnection, method: String, url: String, body: String = "") {
        val secret = BuildConfig.CDICT_APP_SIGN_SECRET
        val id = installId
        if (secret.isBlank() || id.isBlank()) return

        val parsedUrl = URL(url)
        if (!isBackendUrl(parsedUrl)) return

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

    private fun isBackendUrl(url: URL): Boolean =
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
