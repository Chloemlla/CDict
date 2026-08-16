package com.chloemlla.cdict.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoTtsClientTest {

    @Test
    fun `signature is 64 lowercase hex hmac sha256`() {
        val sig = VivoTtsClient.hmacSha256Hex(
            key = "9925f42b456c96de8e424ddc7c06d5d9",
            data = "appId=1336541186&deviceid=00000000000000&nonce_str=AbCdEf1234567890&taskid=t1&text=dGVzdA==&key=9925f42b456c96de8e424ddc7c06d5d9",
        )
        assertEquals(64, sig.length)
        assertTrue(sig.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `signature matches offline oracle vector`() {
        val sig = VivoTtsClient.hmacSha256Hex(
            key = "9925f42b456c96de8e424ddc7c06d5d9",
            data = "appId=1336541186&deviceid=00000000000000&nonce_str=AbCdEf1234567890&taskid=t1&text=dGVzdA==&key=9925f42b456c96de8e424ddc7c06d5d9",
        )
        assertEquals("6489b3c239081cfd66fdc5d721f6fb941d14d09a8c16bc4bef7cb31a03780cc5", sig)
    }

    @Test
    fun `byte array formats to lowercase hex`() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10.toByte(), 0xff.toByte(), 1)
        assertEquals("000f10ff01", bytes.toHex())
    }

    @Test
    fun `accent maps to tts langType`() {
        assertEquals("en-GBR", Accent.UK.ttsLangType)
        assertEquals("en-USA", Accent.US.ttsLangType)
        assertEquals(1, Accent.UK.youdaoType)
        assertEquals(2, Accent.US.youdaoType)
    }
}