package com.chloemlla.cdict.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PronunciationDiagnosticsTest {
    @Test
    fun `record keeps last fallback and clear resets`() {
        PronunciationDiagnostics.clear()
        assertNull(PronunciationDiagnostics.lastFallback.value)

        PronunciationDiagnostics.record(
            FallbackDiagnostics("hello", Accent.US, "vivo TTS 拒绝 errorCode=3010", "HTTP 502")
        )
        val diag = PronunciationDiagnostics.lastFallback.value
        assertEquals("hello", diag?.text)
        assertEquals(Accent.US, diag?.accent)
        assertEquals("vivo TTS 拒绝 errorCode=3010", diag?.vivoReason)
        assertEquals("HTTP 502", diag?.youdaoReason)
    }
}