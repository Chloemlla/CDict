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
    val supporters: List<String>,
)

sealed interface DonationOutcome {
    data class Success(val info: DonationInfo) : DonationOutcome
    data class Failure(val message: String) : DonationOutcome
}

/** 署名申请的结果；[Accepted.duplicated] 为 true 表示同一交易号之前已提交过，仍在等待核实。 */
sealed interface DonationClaimOutcome {
    data class Accepted(val message: String, val duplicated: Boolean) : DonationClaimOutcome
    data class Rejected(val message: String) : DonationClaimOutcome
}

/**
 * 赞赏码客户端：渠道列表、鸣谢名单与二维码图片都实时向自有后端 [CDictBackend] 请求，安装包内不内置收款码。
 *
 * 图片地址一律由 [imageUrl] 用渠道 id 拼出，不使用响应里的绝对地址；后端会把图片请求 302 到运营方在
 * 后台填写的图床地址，因此取图这一跳会直连该图床，图片字节不经过后端。
 */
class DonationClient(
    private val baseUrl: String = CDictBackend.BASE_URL,
    private val transport: suspend (url: String) -> DonationHttpResponse = ::httpGet,
    private val postTransport: suspend (url: String, body: String) -> DonationHttpResponse = ::httpPostJson,
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

    /**
     * 提交署名申请：只上传交易号与希望展示的称呼两项，不带任何设备信息。
     *
     * 同一交易号重复提交是幂等的，后端会回 [DonationClaimOutcome.Accepted] 且 duplicated=true。
     */
    suspend fun submitClaim(transactionId: String, displayName: String): DonationClaimOutcome {
        val id = transactionId.trim()
        val name = displayName.trim()
        if (!isSafeTransactionId(id)) {
            return DonationClaimOutcome.Rejected("交易号格式不对：请填 6-64 位的字母、数字、连字符或下划线")
        }
        if (name.isEmpty() || name.length > 32) {
            return DonationClaimOutcome.Rejected("请填写希望展示的称呼，最多 32 个字")
        }
        val body = JSONObject()
            .put("transactionId", id)
            .put("displayName", name)
            .toString()
        val response = try {
            postTransport(baseUrl + CDictBackend.DONATE_PATH + CDictBackend.DONATE_CLAIM_SUFFIX, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DonationClaimOutcome.Rejected("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
        return parseClaim(response)
    }

    internal fun parseClaim(response: DonationHttpResponse): DonationClaimOutcome {
        val json = runCatching { JSONObject(String(response.bytes, Charsets.UTF_8)) }.getOrNull()
        if (response.status !in 200..299) {
            val detail = json?.optString("error")?.takeIf { it.isNotBlank() }
                ?: json?.optString("message")?.takeIf { it.isNotBlank() }
            return DonationClaimOutcome.Rejected(detail ?: "提交失败（HTTP ${response.status}）")
        }
        if (json == null) return DonationClaimOutcome.Rejected("响应格式异常")
        if (!json.optBoolean("success")) {
            return DonationClaimOutcome.Rejected(json.optString("error").ifBlank { "提交失败" })
        }
        val duplicated = json.optBoolean("duplicated")
        val fallback = if (duplicated) {
            "这个交易号已经提交过了，正在等待核实"
        } else {
            "已提交，开发者核实后会把你的名字加入鸣谢名单"
        }
        return DonationClaimOutcome.Accepted(
            message = json.optString("message").ifBlank { fallback },
            duplicated = duplicated,
        )
    }

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
        val supportersArray = json.optJSONArray("supporters")
        val supporters = buildList {
            for (i in 0 until (supportersArray?.length() ?: 0)) {
                val name = supportersArray?.optString(i)?.trim().orEmpty()
                if (name.isNotEmpty()) add(name.take(32))
            }
        }
        return DonationOutcome.Success(
            DonationInfo(
                notice = json.optString("notice").takeIf { it.isNotBlank() },
                channels = channels,
                supporters = supporters,
            ),
        )
    }
}

/** 渠道 id 只允许小写字母、数字与连字符，避免拼出越界路径。 */
private fun isSafeChannelId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 32 && id.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

/** 交易号字符集与后端保持一致，本地先挡一次，省掉必然被拒的请求。 */
private fun isSafeTransactionId(id: String): Boolean =
    id.length in 6..64 &&
        id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

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

private suspend fun httpPostJson(url: String, body: String): DonationHttpResponse = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }
    try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        DonationHttpResponse(status, stream?.use { it.readBytes() } ?: ByteArray(0))
    } finally {
        connection.disconnect()
    }
}
