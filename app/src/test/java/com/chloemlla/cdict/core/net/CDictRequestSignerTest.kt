package com.chloemlla.cdict.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
