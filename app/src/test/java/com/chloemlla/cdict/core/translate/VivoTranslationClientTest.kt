package com.chloemlla.cdict.core.translate

import com.chloemlla.cdict.core.net.CDictBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoTranslationClientTest {

    private val supportedCodes = setOf(
        "de", "en", "es", "fr", "hi", "id", "it", "ja", "ko", "pt", "ru", "th", "vi",
        "zh-CHS", "zh", "auto",
    )

    @Test
    fun `every translation direction is canonical and within gateway supported set`() {
        assertEquals(21, TranslationDirection.entries.size)
        TranslationDirection.entries.forEach { direction ->
            assertTrue("empty from: ${direction.name}", direction.from.isNotEmpty())
            assertTrue("empty to: ${direction.name}", direction.to.isNotEmpty())
            assertTrue("unsupported from ${direction.from} at ${direction.name}", supportedCodes.contains(direction.from))
            assertTrue("unsupported to ${direction.to} at ${direction.name}", supportedCodes.contains(direction.to))
            assertFalse("bare zh (not zh-CHS): ${direction.name}", direction.from == "zh" || direction.to == "zh")
        }
    }

    @Test
    fun `java url encode mirrors javascript javaEncode`() {
        assertEquals("Hello%2C+world%21+%E4%BD%A0%E5%A5%BD", javaUrlEncode("Hello, world! 你好"))
        assertEquals("a%7Eb", javaUrlEncode("a~b"))
        assertEquals("a%2Bb", javaUrlEncode("a+b"))
        assertEquals("a-b_c.d*e", javaUrlEncode("a-b_c.d*e"))
    }

    @Test
    fun `build form only carries business fields`() {
        val form = buildTranslationForm(
            texts = listOf("你好", "世界"),
            direction = TranslationDirection.ZH_TO_EN,
        )
        assertEquals("你好\n世界", form["text"])
        assertEquals("zh-CHS", form["from"])
        assertEquals("en", form["to"])
        assertEquals(setOf("text", "from", "to"), form.keys)
        assertEquals(TranslationDirection.ZH_TO_EN.label, "中文→英文")
    }

    @Test
    fun `encode form percent encodes values`() {
        val encoded = encodeForm(mapOf("text" to "hi\n你", "from" to "en"))
        assertTrue(encoded.contains("text=hi%0A%E4%BD%A0"))
        assertTrue(encoded.contains("from=en"))
    }

    @Test
    fun `parses success with translation lines and metadata`() {
        val resp = HttpResponse(
            200,
            """{"retcode":0,"code":0,"data":{"translation":"你好\n世界","from":"en","to":"zh-CHS","phonetic":"/test/","freq":null}}""",
        )
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Success
        assertEquals(listOf("你好", "世界"), outcome.result.translations)
        assertEquals("en", outcome.result.from)
        assertEquals("zh-CHS", outcome.result.to)
        assertEquals("/test/", outcome.result.phonetic)
    }

    @Test
    fun `empty translation yields empty list`() {
        val resp = HttpResponse(200, """{"retcode":0,"code":0,"data":{"translation":""}}""")
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Success
        assertEquals(emptyList<String>(), outcome.result.translations)
    }

    @Test
    fun `phonetic json blob is normalized to its text field`() {
        val resp = HttpResponse(
            200,
            """{"retcode":0,"code":0,"data":{"translation":"服务器","from":"auto","to":"zh-CHS","phonetic":"[{\"filename\":\"https://openapi.youdao.com/vivo/ttsapi?q=%E6%9C%8D%E5%8A%A1%E5%99%A8&langType=zh-CHS&appKey=48fd18fd98fb24b2\",\"ttsId\":\"x-phonetic-0\",\"text\":\"fú wù qì\",\"type\":\"auto\"}]"}}""",
        )
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Success
        assertEquals("fú wù qì", outcome.result.phonetic)
        assertEquals("fú wù qì", normalizePhonetic("""[{"text":"fú wù qì","type":"auto"}]"""))
    }

    @Test
    fun `plain phonetic string passes through unchanged`() {
        assertEquals("/test/", normalizePhonetic("/test/"))
        assertEquals("fú wù qì", normalizePhonetic("fú wù qì"))
    }

    @Test
    fun `malformed phonetic json is dropped instead of leaking raw`() {
        assertEquals("junk", normalizePhonetic("junk"))
        assertNull(normalizePhonetic("""[{"text":""]"""))
        assertNull(
            normalizePhonetic(
                """[{"filename":"https://openapi.youdao.com/vivo/ttsapi?q=depending&appKey=48fd18fd98fb24b2","ttsId":"x-phonetic-0","type":"auto"}]""",
            ),
        )
        assertNull(normalizePhonetic("https://openapi.youdao.com/vivo/ttsapi?q=depending"))
    }

    @Test
    fun `service error code yields failure`() {
        val resp = HttpResponse(200, """{"retcode":0,"code":71001,"msg":"bad param"}""")
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Failure
        assertTrue(outcome.message.contains("71001"))
    }

    @Test
    fun `retcode 10000 yields failure`() {
        val resp = HttpResponse(200, """{"retcode":10000,"code":0,"msg":"server"}""")
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Failure
        assertTrue(outcome.message.contains("10000"))
    }

    @Test
    fun `non json body yields failure`() {
        val resp = HttpResponse(200, "oops")
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Failure
        assertTrue(outcome.message.startsWith("非 JSON 响应"))
    }

    @Test
    fun `http 401 yields failure`() {
        val resp = HttpResponse(401, """{"code":401}""")
        val outcome = parseTranslationResponse(resp) as TranslationOutcome.Failure
        assertTrue(outcome.message.contains("401"))
    }

    @Test
    fun `non 2xx without non-zero code is a failure not an empty success`() {
        // 网关/限流的错误体形如 {"error":…}，若只看 code 会被当成"成功但无译文"而静默显示空结果。
        val waf = parseTranslationResponse(HttpResponse(400, """{"error":"请求内容包含非法字符"}"""))
        assertTrue((waf as TranslationOutcome.Failure).message.contains("请求内容包含非法字符"))
        val limited = parseTranslationResponse(HttpResponse(429, """{"message":"请求过于频繁"}"""))
        assertTrue((limited as TranslationOutcome.Failure).message.contains("429"))
        val html = parseTranslationResponse(HttpResponse(502, "<html>Bad Gateway</html>"))
        assertTrue((html as TranslationOutcome.Failure).message.contains("502"))
    }

    @Test
    fun `translate posts encoded form to own backend only`() = runTest {
        var capturedUrl: String? = null
        var capturedHeaders: Map<String, String>? = null
        var capturedBody: String? = null
        val client = VivoTranslationClient(
            transport = { url, headers, body ->
                capturedUrl = url
                capturedHeaders = headers
                capturedBody = body
                HttpResponse(
                    200,
                    """{"retcode":0,"code":0,"data":{"translation":"你好\n世界","from":"en","to":"zh-CHS"}}""",
                )
            },
        )
        val outcome = client.translate(
            TranslationRequest(listOf("hello", "world"), TranslationDirection.EN_TO_ZH)
        ) as TranslationOutcome.Success
        assertEquals(listOf("你好", "世界"), outcome.result.translations)
        assertEquals(CDictBackend.BASE_URL + CDictBackend.TRANSLATE_PATH, capturedUrl)
        assertTrue(capturedUrl!!.startsWith("https://tts.chloemlla.com/"))
        assertTrue(capturedHeaders!!.containsKey("Content-Type"))
        val body = capturedBody!!
        assertTrue(body.contains("text=hello%0Aworld"))
        assertTrue(body.contains("from=en"))
        assertTrue(body.contains("to=zh-CHS"))
        assertFalse(body.contains("appId"))
        assertFalse(body.contains("user_id"))
        capturedHeaders!!.keys.forEach { assertFalse(it.startsWith("X-AI-GATEWAY")) }
    }

    @Test
    fun `transport exception yields network failure`() = runTest {
        val client = VivoTranslationClient(
            transport = { _, _, _ -> throw RuntimeException("boom") },
        )
        val outcome = client.translate(TranslationRequest(listOf("hi"), TranslationDirection.AUTO_TO_ZH))
        assertTrue((outcome as TranslationOutcome.Failure).message.contains("网络请求失败"))
    }

    @Test
    fun `fetchLanguages parses top-level string array shape`() = runTest {
        val client = VivoTranslationClient(getTransport = { url ->
            assertEquals(CDictBackend.BASE_URL + CDictBackend.LANGUAGES_PATH, url)
            HttpResponse(200, """["zh-CHS","EN","ja","ko"]""")
        })
        val outcome = client.fetchLanguages() as LanguageListOutcome.Success
        assertEquals(setOf("zh-chs", "en", "ja", "ko"), outcome.languages)
    }

    @Test
    fun `fetchLanguages parses object with code fields under data list`() = runTest {
        val client = VivoTranslationClient(getTransport = {
            HttpResponse(200, """{"retcode":0,"code":0,"data":{"list":[{"code":"en"},{"lang":"zh-CHS"},{"langCode":"Ja"},{"code":"fr"}]}}""")
        })
        val outcome = client.fetchLanguages() as LanguageListOutcome.Success
        assertEquals(setOf("en", "zh-chs", "ja", "fr"), outcome.languages)
    }

    @Test
    fun `fetchLanguages returns empty set on unexpected shape without throwing`() = runTest {
        val client = VivoTranslationClient(getTransport = { HttpResponse(200, """{"foo":123}""") })
        val outcome = client.fetchLanguages() as LanguageListOutcome.Success
        assertEquals(emptySet<String>(), outcome.languages)
    }

    @Test
    fun `fetchLanguages non 200 yields failure`() = runTest {
        val client = VivoTranslationClient(getTransport = { HttpResponse(500, "boom") })
        assertTrue((client.fetchLanguages() as LanguageListOutcome.Failure).message.contains("500"))
    }

    @Test
    fun `fetchLanguages malformed body yields failure without throwing`() = runTest {
        val client = VivoTranslationClient(getTransport = { HttpResponse(200, "not json") })
        assertTrue((client.fetchLanguages() as LanguageListOutcome.Failure).message.contains("非 JSON"))
    }
}
