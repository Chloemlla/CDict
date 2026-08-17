package com.chloemlla.cdict.core.audio

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * vivo 语音合成（/fy/tts）客户端，逆向对应 speechsdk ttsonline（Protocol.java 字段布局）与
 * libspeech_sec.so 的 sign JNI 签名。
 *
 * 签名算法（libspeech_sec.so 0x2b44c 反编译确认）：
 *   1. 参数按 key 升序拼接：appId & deviceid & nonce_str & taskid & text
 *   2. hmacHex = hex(HMAC-SHA256(appKey, joinedParams))
 *   3. sign = hex(MD5(hmacHex + "&key=" + appKey))
 *
 * 请求体为 JSON（非表单）；成功时返回音频二进制（aue=3 → MP3），失败时返回
 * {"errorResult":{"errorCode":…,"errorMsg":…}}。
 *
 * TTS 使用独立于文本翻译的凭证 appId=1336541186 / appKey=9925f42b456c96de8e424ddc7c06d5d9。
 */
class VivoTtsClient(
    private val url: String = "https://vivotrans.vivo.com.cn/fy/tts",
    private val appId: String = "1336541186",
    private val appKey: String = "9925f42b456c96de8e424ddc7c06d5d9",
) {
    /** 请求 TTS 合成。成功返回 decodable(0,0) 的 [VivoTtsResult.Audio]；否则 [VivoTtsResult.Error]。 */
    suspend fun synthesize(
        text: String,
        langType: String = "en-USA",
        vcn: String = "women",
        aue: Int = 3,
        speed: Int = 70,
        volume: Int = 50,
        pitch: Int = 50,
        deviceId: String = DEFAULT_DEVICE_ID,
    ): VivoTtsResult = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID().toString().replace("-", "")
        val nonce = randomAlphanumeric(16)
        val textB64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        val params = "appId=$appId&deviceid=$deviceId&nonce_str=$nonce&taskid=$taskId&text=$textB64"
        val hmacHex = hmacSha256Hex(appKey, params)
        val sign = md5Hex("$hmacHex&key=$appKey")
        // 用 org.json 统一构建：手工拼接易漏引号，曾致字段未加引号被服务端 JSON parse 400
        // （先前 deviceid 前导零掩盖了它；去掉后服务器继续解析就撞上未引号的 taskid 等）。
        val body = JSONObject()
            .put("appId", appId)
            .put("deviceid", deviceId)
            .put("taskid", taskId)
            .put("nonce_str", nonce)
            .put("aue", aue)
            .put("auf", "audio/L16;rate=16000")
            .put("vcn", vcn)
            .put("speed", speed)
            .put("volume", volume)
            .put("pitch", pitch)
            .put("langType", langType)
            .put("text", textB64)
            .put("encoding", "utf-8")
            .put("sign", sign)
            .put("sysVer", "14")
            .put("product", "PD2243")
            .put("model", "V2309A")
            .put("appVer", "4.5.9.0")
            .put("app", "com.vivo.translator")
            .toString()
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Content-Length", body.toByteArray(Charsets.UTF_8).size.toString())
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status in 200..299) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    val err = parseErrorResult(bytes)
                    if (err != null) VivoTtsResult.Error(err) else VivoTtsResult.Audio(bytes)
                } else {
                    val err = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    VivoTtsResult.Error("HTTP $status $err".trim())
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            VivoTtsResult.Error("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun randomAlphanumeric(length: Int): String = buildString {
        repeat(length) {
            append(ALPHANUMERIC[RandomSource.nextInt(ALPHANUMERIC.length)])
        }
    }

    /** 2xx 响应体若是 {"errorResult":{...}} 则返回诊断文案，否则返回 null（视为正常音频）。 */
    internal fun parseErrorResult(bytes: ByteArray): String? {
        val s = String(bytes.take(1024).toByteArray(), Charsets.UTF_8)
        if (!s.contains("errorResult")) return null
        val code = Regex("\"errorCode\"\\s*:\\s*\"?([^,\"}\\s]+)\"?").find(s)?.groupValues?.get(1)
        val msg = Regex("\"errorMsg\"\\s*:\\s*\"([^\"]*)\"").find(s)?.groupValues?.get(1)
        return buildString {
            append("vivo TTS 拒绝")
            if (code != null) append(" errorCode=$code")
            if (msg != null) append(" errorMsg=$msg")
        }
    }

    private object RandomSource {
        private val random = java.util.Random()
        fun nextInt(bound: Int): Int = random.nextInt(bound)
    }

    companion object {
        private const val ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        // 不能带前导零：vivo 服务端把 deviceid 按数值字段反序列化（Spring/Jackson 默认拒绝
        // numeric 类型带前导零，会报 HTTP 400 "Leading zeroes not allowed"）。旧值 00000000000000 触发。
        const val DEFAULT_DEVICE_ID = "0"

        fun hmacSha256Hex(key: String, data: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
        }

        fun md5Hex(data: String): String =
            MessageDigest.getInstance("MD5").digest(data.toByteArray(Charsets.UTF_8)).toHex()
    }
}

internal fun ByteArray.toHex(): String = buildString {
    val hex = "0123456789abcdef"
    for (b in this@toHex) {
        val v = b.toInt() and 0xff
        append(hex[v ushr 4])
        append(hex[v and 0x0f])
    }
}

sealed interface VivoTtsResult {
    data class Audio(val bytes: ByteArray) : VivoTtsResult
    data class Error(val message: String) : VivoTtsResult
}