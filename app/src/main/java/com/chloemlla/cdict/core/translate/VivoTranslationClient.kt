package com.chloemlla.cdict.core.translate

import com.chloemlla.cdict.core.net.CDictBackend
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在线翻译客户端：只请求 CDict 自有后端 [CDictBackend]，由服务端代理到上游翻译网关。
 *
 * 上游地址、appId/appKey 与网关签名全部留在服务端，安装包内不含任何第三方凭据；
 * 服务端沿用上游的响应结构（retcode/code/data.translation），因此解析逻辑与之前一致。
 */
data class HttpResponse(val status: Int, val body: String)

class VivoTranslationClient(
    private val serverUrl: String = CDictBackend.BASE_URL,
    private val transport: suspend (url: String, headers: Map<String, String>, body: String) -> HttpResponse = ::httpPost,
    private val getTransport: suspend (url: String) -> HttpResponse = ::httpGet,
) {
    suspend fun translate(request: TranslationRequest): TranslationOutcome {
        val body = encodeForm(buildTranslationForm(request.texts, request.direction))
        val headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded",
            "Accept" to "application/json",
            "Content-Length" to body.toByteArray(Charsets.UTF_8).size.toString(),
        )
        val response = try {
            transport(serverUrl + PATH, headers, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TranslationOutcome.Failure("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
        return parseTranslationResponse(response)
    }

    /** GET 语言列表：服务端透传上游支持的语言集合；异常→Failure，非 200→Failure，结构未知时兜底为空集 Success。 */
    suspend fun fetchLanguages(): LanguageListOutcome {
        val url = serverUrl + LANG_LIST_PATH
        val response = try {
            getTransport(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return LanguageListOutcome.Failure("网络请求失败: ${e.message ?: e.javaClass.simpleName}")
        }
        return parseLanguageListResponse(response)
    }

    companion object {
        private const val PATH = CDictBackend.TRANSLATE_PATH
        private const val LANG_LIST_PATH = CDictBackend.LANGUAGES_PATH
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

private suspend fun httpGet(url: String): HttpResponse =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

/** 稳健解析语言列表：兼容顶层字符串数组 / 对象下 data·list·languages·data.list·langs 等路径；
 *  元素可为纯字符串码或含 code/lang/langCode/name 字段的对象。异常/未知结构→空集 Success，绝不抛异常。 */
internal fun parseLanguageListResponse(resp: HttpResponse): LanguageListOutcome {
    if (resp.status !in 200..299) {
        return LanguageListOutcome.Failure("语言列表请求失败 HTTP ${resp.status}")
    }
    val json: Any = try {
        if (resp.body.trimStart().startsWith("[")) JSONArray(resp.body) else JSONObject(resp.body)
    } catch (e: Exception) {
        return LanguageListOutcome.Failure("语言列表非 JSON 响应: ${resp.body.take(300)}")
    }
    val languages = linkedSetOf<String>()
    fun collectArray(arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            val code: String = when (item) {
                is String -> item
                is JSONObject -> listOf("code", "lang", "langCode", "name")
                    .mapNotNull { key -> item.optString(key).takeIf { it.isNotEmpty() } }
                    .firstOrNull().orEmpty()
                else -> ""
            }
            val normalized = code.trim().lowercase()
            if (normalized.isNotEmpty()) languages.add(normalized)
        }
    }
    when (json) {
        is JSONArray -> collectArray(json)
        is JSONObject -> {
            val data = json.optJSONObject("data")
            listOf(
                json.optJSONArray("data"),
                data?.optJSONArray("list"),
                data?.optJSONArray("languages"),
                json.optJSONArray("list"),
                json.optJSONArray("languages"),
                json.optJSONArray("langs"),
            ).forEach(::collectArray)
        }
    }
    return LanguageListOutcome.Success(languages)
}

/** 与 java.net.URLEncoder 语义一致：空格 -> '+', 安全字符 A-Za-z0-9.-*_ 不编码，其余 UTF-8 大写百分号编码。 */
internal fun javaUrlEncode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())

/** 请求体只保留业务字段：原文、源语言、目标语言；appId/设备参数/签名由服务端补全。 */
internal fun buildTranslationForm(
    texts: List<String>,
    direction: TranslationDirection,
): LinkedHashMap<String, String> = linkedMapOf(
    "text" to texts.joinToString("\n"),
    "from" to direction.from,
    "to" to direction.to,
)

internal fun encodeForm(fields: Map<String, String>): String =
    fields.entries.joinToString("&") { (k, v) -> "${javaUrlEncode(k)}=${javaUrlEncode(v)}" }

internal fun parseTranslationResponse(resp: HttpResponse): TranslationOutcome {
    // 非 2xx 必须先拦：网关或限流的错误体只有 {"error":…} 而没有非零 code，
    // 落到下面的 code == 0 分支会被当成"成功但无译文"，界面静默显示空结果。
    if (resp.status !in 200..299) {
        val detail = runCatching { JSONObject(resp.body) }.getOrNull()
            ?.let { j -> listOf("error", "message", "msg").firstNotNullOfOrNull { k -> j.optString(k).takeIf { it.isNotEmpty() } } }
            ?: resp.body.take(200)
        return TranslationOutcome.Failure("HTTP ${resp.status} $detail".trim())
    }
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
            phonetic = data.optString("phonetic").takeIf { it.isNotEmpty() }?.let(::normalizePhonetic),
        )
    )
}

/**
 * 归一化 vivo 网关返回的 phonetic 字段为可展示的纯音标字符串,不可展示时返回 null。
 *
 * 中文等场景下该字段不是纯 IPA,而是一段 JSON(数组,元素含 filename / ttsId / text / type),
 * 例如 `[{"filename":"https://openapi.youdao.com/vivo/ttsapi?...&appKey=...","ttsId":"…","text":"fú wù qì","type":"auto"}]`。
 * 抽取各元素的 `text`(拼音/音标)拼接;抽不出可展示文本(字段缺失、JSON 被截断)时返回 null——
 * 一旦回退成原样返回,内部 TTS URL 与 appKey 就会出现在译文下方,这是必须避免的泄露面。
 */
internal fun normalizePhonetic(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val jsonShaped = trimmed.startsWith("[") || trimmed.startsWith("{")
    if (!jsonShaped) return trimmed.takeUnless(::carriesInternals)
    val parsed: Any? = runCatching {
        if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
    }.getOrNull()
    val texts = ArrayList<String>()
    val queue = ArrayDeque<Any?>()
    queue.add(parsed)
    while (queue.isNotEmpty()) {
        when (val node = queue.removeFirst()) {
            is JSONArray -> for (i in 0 until node.length()) queue.add(node.opt(i))
            is JSONObject -> {
                node.optString("text")
                    .takeIf { it.isNotEmpty() && !carriesInternals(it) }
                    ?.let(texts::add)
                node.keys().forEach { key -> queue.add(node.opt(key)) }
            }
            else -> {}
        }
    }
    return texts.joinToString("，").takeIf { it.isNotEmpty() }
}

/** 含链接或网关凭据痕迹的字符串一律不展示。 */
private fun carriesInternals(value: String): Boolean =
    "http://" in value || "https://" in value || "appKey" in value || "ttsId" in value
