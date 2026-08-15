package com.chloemlla.cdict.core.translate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoTranslationClientTest {

    @Test
    fun `java url encode mirrors javascript javaEncode`() {
        assertEquals("Hello%2C+world%21+%E4%BD%A0%E5%A5%BD", javaUrlEncode("Hello, world! 你好"))
        assertEquals("a%7Eb", javaUrlEncode("a~b"))
        assertEquals("a%2Bb", javaUrlEncode("a+b"))
        assertEquals("a-b_c.d*e", javaUrlEncode("a-b_c.d*e"))
    }

    @Test
    fun `signature matches translate js vector`() {
        val signature = vivoSignature(
            appKey = "eORMflYNZwgqlvua",
            appId = "9023957766",
            path = "/translation/query",
            timestamp = "1755200000",
            nonce = "a1b2c3d4e5",
        )
        assertEquals("2F8aNDYFE5s3iybNvChm/GrlaDNMz3T/BkYIXHOSDMo=", signature)
    }

    @Test
    fun `build form contains expected fields`() {
        val form = buildTranslationForm(
            texts = listOf("你好", "世界"),
            direction = TranslationDirection.ZH_TO_EN,
            appId = "9023957766",
            userId = "com.vivo.translator",
        )
        assertEquals("你好\n世界", form["text"])
        assertEquals("zh-CHS", form["from"])
        assertEquals("en", form["to"])
        assertEquals("9023957766", form["appId"])
        assertEquals("com.vivo.translator", form["app"])
        assertEquals("com.vivo.translator", form["user_id"])
        assertEquals("00000000000000", form["em"])
        assertTrue(form.containsKey("requestId"))
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
    fun `translate posts encoded form and parses response`() = runTest {
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
        assertEquals("https://vivotrans.vivo.com/translation/query", capturedUrl)
        assertTrue(capturedHeaders!!.containsKey("Content-Type"))
        val body = capturedBody!!
        assertTrue(body.contains("text=hello%0Aworld"))
        assertTrue(body.contains("from=en"))
        assertTrue(body.contains("to=zh-CHS"))
        assertTrue(body.contains("appId=9023957766"))
        assertTrue(body.contains("app=com.vivo.translator"))
        assertTrue(capturedHeaders!!.containsKey("X-AI-GATEWAY-APP-ID").not())
    }

    @Test
    fun `signed request adds X-AI-GATEWAY headers`() = runTest {
        var capturedHeaders: Map<String, String>? = null
        val client = VivoTranslationClient(
            sign = true,
            transport = { _, headers, _ ->
                capturedHeaders = headers
                HttpResponse(200, """{"retcode":0,"code":0,"data":{"translation":"ok"}}""")
            },
        )
        val outcome = client.translate(TranslationRequest(listOf("hi"), TranslationDirection.AUTO_TO_ZH))
        assertTrue(outcome is TranslationOutcome.Success)
        val headers = capturedHeaders!!
        assertEquals("9023957766", headers["X-AI-GATEWAY-APP-ID"])
        assertTrue(headers.containsKey("X-AI-GATEWAY-TIMESTAMP"))
        assertTrue(headers.containsKey("X-AI-GATEWAY-NONCE"))
        assertEquals(
            "x-ai-gateway-app-id;x-ai-gateway-timestamp;x-ai-gateway-nonce",
            headers["X-AI-GATEWAY-SIGNED-HEADERS"],
        )
        assertTrue(headers["X-AI-GATEWAY-SIGNATURE"].isNullOrEmpty().not())
    }

    @Test
    fun `transport exception yields network failure`() = runTest {
        val client = VivoTranslationClient(
            transport = { _, _, _ -> throw RuntimeException("boom") },
        )
        val outcome = client.translate(TranslationRequest(listOf("hi"), TranslationDirection.AUTO_TO_ZH))
        assertTrue((outcome as TranslationOutcome.Failure).message.contains("网络请求失败"))
    }
}
