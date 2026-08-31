package com.chloemlla.cdict.core.net

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CDictRequestSignerTest {
    private val secret = "test-cdict-secret"
    private val installId = "2d87ce39-5c4f-4a70-a950-cfb63805a7dc"
    private val timestamp = "1700000000000"
    private val nonce = "00112233445566778899aabbccddeeff"

    @Test
    fun `signature matches backend canonical format`() {
        val headers = buildCdictSignatureHeaders(
            secret = secret,
            installId = installId,
            method = "POST",
            requestTarget = "/api/cdict/translate",
            body = "text=hello&from=auto&to=zh-CHS",
            timestamp = timestamp,
            nonce = nonce,
        )

        assertEquals("1", headers["X-CDict-Sig-Version"])
        assertEquals(timestamp, headers["X-CDict-Ts"])
        assertEquals(nonce, headers["X-CDict-Nonce"])
        assertEquals(installId, headers["X-CDict-Install"])
        assertEquals(
            "c3c70c7e3e9d219ff4576d78eb552e8c82df3040113f2cfc85c5eb540743abda",
            headers["X-CDict-Sig"],
        )
    }

    @Test
    fun `body and install id are bound into signature`() {
        val original = signature(body = "text=hello", installId = installId)

        assertNotEquals(original, signature(body = "text=changed", installId = installId))
        assertNotEquals(original, signature(body = "text=hello", installId = "another-install-id"))
    }

    @Test
    fun `query is bound into signature`() {
        val original = signature(
            method = "GET",
            body = "",
            installId = installId,
            requestTarget = "/api/cdict/tts?source=engine&text=hello",
        )

        assertNotEquals(
            original,
            signature(
                method = "GET",
                body = "",
                installId = installId,
                requestTarget = "/api/cdict/tts?source=engine&text=changed",
            ),
        )
    }

    private fun signature(
        method: String = "POST",
        body: String,
        installId: String,
        requestTarget: String = "/api/cdict/translate",
    ): String? =
        buildCdictSignatureHeaders(
            secret = secret,
            installId = installId,
            method = method,
            requestTarget = requestTarget,
            body = body,
            timestamp = timestamp,
            nonce = nonce,
        )["X-CDict-Sig"]

    /**
     * 每个 [CDictBackend] 端点都必须被判成后端地址，否则 [CDictRequestSigner.sign] 会静默跳过
     * 签名，线上表现只是「经常被限流」。后端换路径前缀时这条测试先红。
     */
    @Test
    fun `every backend endpoint is recognised as signable`() {
        val base = CDictBackend.BASE_URL
        listOf(
            base + CDictBackend.TRANSLATE_PATH,
            base + CDictBackend.LANGUAGES_PATH,
            base + CDictBackend.TTS_PATH,
            base + CDictBackend.TTS_PATH + "?source=engine&text=hello",
            base + CDictBackend.DONATE_PATH,
            base + CDictBackend.DONATE_PATH + "/alipay",
            base + CDictBackend.DONATE_PATH + CDictBackend.DONATE_CLAIM_SUFFIX,
        ).forEach { url ->
            assertTrue(url, CDictRequestSigner.isBackendUrl(URL(url)))
        }
    }

    @Test
    fun `foreign hosts and unrelated paths are never signed`() {
        listOf(
            "https://tts.chloemlla.com",
            "https://tts.chloemlla.com/api/other",
            "https://tts.chloemlla.com/api/cdictx/translate",
            "https://tts.chloemlla.com.example.invalid/api/cdict/translate",
            "https://example.invalid/api/cdict/translate",
            "http://tts.chloemlla.com/api/cdict/translate",
        ).forEach { url ->
            assertFalse(url, CDictRequestSigner.isBackendUrl(URL(url)))
        }
    }
}
