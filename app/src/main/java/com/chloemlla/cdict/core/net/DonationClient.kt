package com.chloemlla.cdict.core.net

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 一个赞赏渠道；[id] 同时用于拼接图片地址，客户端不接受服务端下发的绝对 URL。 */
data class DonationChannel(
    val id: String,
    val name: String,
    val hint: String?,
)

data class DonationInfo(
    val notice: String?,
    val channels: List<DonationChannel>,
)

sealed interface DonationOutcome {
    data class Success(val info: DonationInfo) : DonationOutcome
    data class Failure(val message: String) : DonationOutcome
}

/**
 * 赞赏码客户端：渠道列表与二维码图片都实时向自有后端 [CDictBackend] 请求，安装包内不内置收款码。
 *
 * 图片地址一律由 [imageUrl] 用渠道 id 拼出，不使用响应里的绝对地址，保证出口只有一个域名。
 */
class DonationClient(
    private val baseUrl: String = CDictBackend.BASE_URL,
    private val transport: suspend (url: String) -> DonationHttpResponse = ::httpGet,
) {
    suspend fun fetchChannels(): DonationOutcome {
        val response = try {
            transport(baseUrl + CDictBackend.DONATE_PATH)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DonationOutcome.Failure("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
        return parseChannels(response)
    }

    suspend fun fetchImage(channelId: String): ByteArray? {
        if (!isSafeChannelId(channelId)) return null
        val response = try {
            transport(imageUrl(channelId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return response.bytes.takeIf { response.status in 200..299 && it.isNotEmpty() }
    }

    internal fun imageUrl(channelId: String): String =
        baseUrl + CDictBackend.DONATE_PATH + "/" + channelId

    internal fun parseChannels(response: DonationHttpResponse): DonationOutcome {
        val body = String(response.bytes, Charsets.UTF_8)
        if (response.status !in 200..299) {
            val detail = runCatching { JSONObject(body).optString("error") }.getOrNull()
            return DonationOutcome.Failure("HTTP ${response.status} ${detail.orEmpty()}".trim())
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return DonationOutcome.Failure("响应格式异常")
        val array = json.optJSONArray("channels")
            ?: return DonationOutcome.Failure(json.optString("error").ifBlank { "暂无可用赞赏渠道" })
        val channels = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (!isSafeChannelId(id)) continue
                add(
                    DonationChannel(
                        id = id,
                        name = item.optString("name").ifBlank { id },
                        hint = item.optString("hint").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        if (channels.isEmpty()) return DonationOutcome.Failure("暂无可用赞赏渠道")
        return DonationOutcome.Success(
            DonationInfo(
                notice = json.optString("notice").takeIf { it.isNotBlank() },
                channels = channels,
            ),
        )
    }
}

/** 渠道 id 只允许小写字母、数字与连字符，避免拼出越界路径。 */
private fun isSafeChannelId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 32 && id.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

class DonationHttpResponse(val status: Int, val bytes: ByteArray)

private suspend fun httpGet(url: String): DonationHttpResponse = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json, image/*")
    }
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        DonationHttpResponse(status, stream?.use { it.readBytes() } ?: ByteArray(0))
    } finally {
        connection.disconnect()
    }
}
