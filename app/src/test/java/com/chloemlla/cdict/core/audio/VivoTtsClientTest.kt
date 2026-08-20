package com.chloemlla.cdict.core.audio

import com.chloemlla.cdict.core.net.CDictBackend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoTtsClientTest {

    @Test
    fun `request url targets own backend engine endpoint only`() {
        val url = VivoTtsClient().buildUrl("hello world", "en-USA")
        assertEquals(
            CDictBackend.BASE_URL + CDictBackend.TTS_PATH +
                "?source=engine&text=hello+world&langType=en-USA",
            url,
        )
        assertTrue(url.startsWith("https://tts.chloemlla.com/"))
    }

    @Test
    fun `request url carries no upstream credential or signature`() {
        val url = VivoTtsClient().buildUrl("测试", "zh-CHS")
        assertTrue(url.contains("text=%E6%B5%8B%E8%AF%95"))
        listOf("appId", "appKey", "sign", "nonce_str", "taskid", "deviceid").forEach {
            assertFalse("credential leaked: $it", url.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `upstream error result json is surfaced with code and message`() {
        val client = VivoTtsClient()
        val body = """{"errorResult":{"errorCode":10101,"errorMsg":"文本过长，无法合成"}}"""
        assertEquals(
            "在线合成拒绝 errorCode=10101 errorMsg=文本过长，无法合成",
            client.parseErrorResult(body.toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun `backend failure json is surfaced with its message`() {
        val client = VivoTtsClient()
        val body = """{"success":false,"code":502,"error":"上游语音合成请求失败","message":"上游语音合成请求失败"}"""
        assertEquals("在线合成拒绝 上游语音合成请求失败", client.parseErrorResult(body.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `audio response is not mistaken for error`() {
        val client = VivoTtsClient()
        assertEquals(
            null,
            client.parseErrorResult(byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00)),
        )
    }

    @Test
    fun `audio content type yields audio result`() = runTest {
        val client = VivoTtsClient(
            transport = { TtsHttpResponse(200, byteArrayOf(1, 2, 3), "audio/L16; rate=16000") },
        )
        val result = client.synthesize("hi") as VivoTtsResult.Audio
        assertEquals(3, result.bytes.size)
    }

    @Test
    fun `json content type on 200 yields error result`() = runTest {
        val client = VivoTtsClient(
            transport = {
                TtsHttpResponse(
                    200,
                    """{"error":"缺少待合成文本"}""".toByteArray(Charsets.UTF_8),
                    "application/json",
                )
            },
        )
        assertEquals("在线合成拒绝 缺少待合成文本", (client.synthesize("hi") as VivoTtsResult.Error).message)
    }

    @Test
    fun `non 2xx status yields error result`() = runTest {
        val client = VivoTtsClient(transport = { TtsHttpResponse(429, ByteArray(0), null) })
        assertEquals("HTTP 429", (client.synthesize("hi") as VivoTtsResult.Error).message)
    }

    @Test
    fun `transport exception yields network error result`() = runTest {
        val client = VivoTtsClient(transport = { throw RuntimeException("boom") })
        assertTrue((client.synthesize("hi") as VivoTtsResult.Error).message.contains("网络请求失败"))
    }

    @Test
    fun `accent maps to tts langType`() {
        assertEquals("en-GBR", Accent.UK.ttsLangType)
        assertEquals("en-USA", Accent.US.ttsLangType)
        assertEquals(1, Accent.UK.youdaoType)
        assertEquals(2, Accent.US.youdaoType)
    }
}
