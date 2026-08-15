package com.chloemlla.cdict.core.translate

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * vivo 翻译机文本翻译网关客户端（逆向，对应 translate.js）。
 *
 * V2 无签名直连 https://vivotrans.vivo.com/translation/query 为默认路径；
 * V3 云校验（translate.vivo.com + X-AI-GATEWAY 签名）仅当 [sign] 为 true 时使用，
 * 本 appId 未开通云校验能力，默认关闭。
 */
data class HttpResponse(val status: Int, val body: String)

class VivoTranslationClient(
    private val serverUrl: String = "https://vivotrans.vivo.com",
    private val appId: String = "9023957766",
    private val appKey: String = "eORMflYNZwgqlvua",
    private val userId: String = "com.vivo.translator",
    private val sign: Boolean = false,
    private val transport: suspend (url: String, headers: Map<String, String>, body: String) -> HttpResponse = ::httpPost,
) {
    suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val body = encodeForm(buildTranslationForm(request.texts, request.direction, appId, userId))
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val headers = mutableMapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "User-Agent" to "okhttp/4.9.1",
            "Content-Length" to body.toByteArray(Charsets.UTF_8).size.toString(),
        )
        if (sign) {
            val nonce = vivoNonce()
            headers["X-AI-GATEWAY-APP-ID"] = appId
            headers["X-AI-GATEWAY-TIMESTAMP"] = timestamp
            headers["X-AI-GATEWAY-NONCE"] = nonce
            headers["X-AI-GATEWAY-SIGNED-HEADERS"] =
                "x-ai-gateway-app-id;x-ai-gateway-timestamp;x-ai-gateway-nonce"
            headers["X-AI-GATEWAY-SIGNATURE"] = vivoSignature(appKey, appId, PATH, timestamp, nonce)
        }
        val response = try {
            transport(serverUrl + PATH, headers, body)
        } catch (e: Exception) {
            return TranslationOutcome.Failure("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
        return parseTranslationResponse(response)
    }

    companion object {
        private const val PATH = "/translation/query"
    }
}

private suspend fun httpPost(url: String, headers: Map<String, String>, body: String): HttpResponse =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 30_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

/** 与 java.net.URLEncoder 语义一致：空格 -> '+', 安全字符 A-Za-z0-9.-*_ 不编码，其余 UTF-8 大写百分号编码。 */
internal fun javaUrlEncode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())

/** 10 位十六进制随机 nonce，对应 unifiedauth 的 b.d(10)。 */
internal fun vivoNonce(): String = buildString {
    repeat(10) { append(kotlin.random.Random.nextInt(16).toString(16)) }
}

/** X-AI-GATEWAY 签名：Base64(HmacSHA256(appKey, canonical 六行))，canonical 的 query 为空（参数在表单体）。 */
internal fun vivoSignature(appKey: String, appId: String, path: String, timestamp: String, nonce: String): String {
    val canonical = listOf(
        "POST",
        path,
        "",
        appId,
        timestamp,
        "x-ai-gateway-app-id:$appId\nx-ai-gateway-timestamp:$timestamp\nx-ai-gateway-nonce:$nonce",
    ).joinToString("\n")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(appKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
}

internal fun buildTranslationForm(
    texts: List<String>,
    direction: TranslationDirection,
    appId: String,
    userId: String,
): LinkedHashMap<String, String> {
    val fields = linkedMapOf(
        "text" to texts.joinToString("\n"),
        "from" to direction.from,
        "to" to direction.to,
        "requestId" to UUID.randomUUID().toString(),
        "appId" to appId,
        "app" to "com.vivo.translator",
        "user_id" to userId,
    )
    fields.putAll(deviceParams())
    return fields
}

internal fun encodeForm(fields: Map<String, String>): String =
    fields.entries.joinToString("&") { (k, v) -> "${javaUrlEncode(k)}=${javaUrlEncode(v)}" }

/** 设备/SDK 参数：服务器主要校验 appId/签名，device 参数值不强制匹配。 */
private fun deviceParams(): Map<String, String> = linkedMapOf(
    "em" to "00000000000000",
    "model" to "V2309A",
    "product" to "PD2243",
    "deviceType" to "mobile",
    "elapsedtime" to "0",
    "av" to "1",
    "an" to "1.0.0",
    "cs" to "0",
    "sysVer" to "14",
    "appVersion" to "1",
    "appVer" to "1.0.0",
    "appPkgName" to "com.vivo.translator",
    "netType" to "2",
    "screensize" to "1080x2400",
    "oaid" to "",
    "vaid" to "00000000000000",
)

internal fun parseTranslationResponse(resp: HttpResponse): TranslationOutcome {
    if (resp.status == 401) return TranslationOutcome.Failure("HTTP 401 云校验失败（签名无效）")
    val json = try {
        JSONObject(resp.body)
    } catch (e: Exception) {
        return TranslationOutcome.Failure("非 JSON 响应: ${resp.body.take(300)}")
    }
    val retcode = json.optInt("retcode")
    val code = json.optInt("code")
    if (retcode == 10000 || code != 0) {
        val msg = json.optString("msg").ifEmpty { json.optString("message") }
        return TranslationOutcome.Failure("服务端错误 code=$code retcode=$retcode msg=$msg")
    }
    val data = json.optJSONObject("data")
    if (data == null) {
        return TranslationOutcome.Success(
            TranslationResult(emptyList(), json.optString("from"), json.optString("to"), null)
        )
    }
    val joined = data.optString("translation")
    return TranslationOutcome.Success(
        TranslationResult(
            translations = if (joined.isEmpty()) emptyList() else joined.split("\n"),
            from = data.optString("from", json.optString("from")),
            to = data.optString("to", json.optString("to")),
            phonetic = data.optString("phonetic").takeIf { it.isNotEmpty() },
        )
    )
}
