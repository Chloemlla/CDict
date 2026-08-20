package com.chloemlla.cdict.core.audio

import com.chloemlla.cdict.core.net.CDictBackend
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 后端音频响应：[bytes] 可能是音频，也可能是错误 JSON，由 [contentType] 区分。 */
class TtsHttpResponse(val status: Int, val bytes: ByteArray, val contentType: String?)

/**
 * 在线语音合成客户端：只请求 CDict 自有后端 [CDictBackend]，由服务端完成实际合成。
 *
 * 凭据与签名全部留在服务端，安装包内不含任何密钥。
 * 服务端成功时返回 audio 类型字节，失败时返回 JSON 诊断。
 */
class VivoTtsClient(
    private val baseUrl: String = CDictBackend.BASE_URL,
    private val transport: suspend (url: String) -> TtsHttpResponse = ::httpGetAudio,
) {
    /** 请求 TTS 合成。成功返回 [VivoTtsResult.Audio]；否则 [VivoTtsResult.Error]。 */
    suspend fun synthesize(text: String, langType: String = "en-USA"): VivoTtsResult {
        val response = try {
            transport(buildUrl(text, langType))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return VivoTtsResult.Error("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
        if (response.status !in 200..299) {
            return VivoTtsResult.Error(parseErrorResult(response.bytes) ?: "HTTP ${response.status}")
        }
        val isAudio = response.contentType?.startsWith("audio/", ignoreCase = true) == true
        if (!isAudio) {
            return VivoTtsResult.Error(parseErrorResult(response.bytes) ?: "在线合成返回非音频响应")
        }
        return VivoTtsResult.Audio(response.bytes)
    }

    internal fun buildUrl(text: String, langType: String): String = buildString {
        append(baseUrl)
        append(CDictBackend.TTS_PATH)
        append("?source=").append(CDictBackend.SOURCE_ENGINE)
        append("&text=").append(URLEncoder.encode(text, Charsets.UTF_8.name()))
        append("&langType=").append(URLEncoder.encode(langType, Charsets.UTF_8.name()))
    }

    /** 非音频响应体转诊断文案：兼容上游 {"errorResult":{…}} 与后端 {"error":…} 两种结构。 */
    internal fun parseErrorResult(bytes: ByteArray): String? {
        val s = String(bytes.take(1024).toByteArray(), Charsets.UTF_8)
        if (s.contains("errorResult")) {
            val code = Regex("\"errorCode\"\\s*:\\s*\"?([^,\"}\\s]+)\"?").find(s)?.groupValues?.get(1)
            val msg = Regex("\"errorMsg\"\\s*:\\s*\"([^\"]*)\"").find(s)?.groupValues?.get(1)
            return buildString {
                append("在线合成拒绝")
                if (code != null) append(" errorCode=$code")
                if (msg != null) append(" errorMsg=$msg")
            }
        }
        val message = Regex("\"(?:error|message|msg)\"\\s*:\\s*\"([^\"]*)\"").find(s)?.groupValues?.get(1)
        return message?.takeIf { it.isNotBlank() }?.let { "在线合成拒绝 $it" }
    }
}

private suspend fun httpGetAudio(url: String): TtsHttpResponse = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 30_000
        readTimeout = 30_000
        setRequestProperty("Accept", "audio/*, application/json")
    }
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        TtsHttpResponse(status, bytes, connection.contentType)
    } finally {
        connection.disconnect()
    }
}

sealed interface VivoTtsResult {
    data class Audio(val bytes: ByteArray) : VivoTtsResult
    data class Error(val message: String) : VivoTtsResult
}
