package com.chloemlla.cdict.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoTtsClientTest {

    @Test
    fun `signature is 32 lowercase hex md5 of hmac sha256`() {
        val hmac = VivoTtsClient.hmacSha256Hex(
            key = "9925f42b456c96de8e424ddc7c06d5d9",
            data = "appId=1336541186&deviceid=00000000000000&nonce_str=AbCdEf1234567890&taskid=t1&text=dGVzdA==",
        )
        assertEquals(64, hmac.length)
        val sign = VivoTtsClient.md5Hex("$hmac&key=9925f42b456c96de8e424ddc7c06d5d9")
        assertEquals(32, sign.length)
        assertTrue(sign.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `signature matches offline oracle vector`() {
        val hmac = VivoTtsClient.hmacSha256Hex(
            key = "9925f42b456c96de8e424ddc7c06d5d9",
            data = "appId=1336541186&deviceid=00000000000000&nonce_str=AbCdEf1234567890&taskid=t1&text=dGVzdA==",
        )
        assertEquals("1547db4e608d4e732c7574e1b839a421f023b29ac48c51aa288e472bc820ac53", hmac)
        val sign = VivoTtsClient.md5Hex("$hmac&key=9925f42b456c96de8e424ddc7c06d5d9")
        assertEquals("891f06f2fcaf97b386feff7bf4f870bb", sign)
    }

    @Test
    fun `byte array formats to lowercase hex`() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10.toByte(), 0xff.toByte(), 1)
        assertEquals("000f10ff01", bytes.toHex())
    }

    @Test
    fun `error result json is detected with code and message`() {
        val client = VivoTtsClient()
        val body = """{"errorResult":{"errorCode":10101,"errorMsg":"文本过长，无法合成"}}"""
        val err = client.parseErrorResult(body.toByteArray(Charsets.UTF_8))
        assertEquals("vivo TTS 拒绝 errorCode=10101 errorMsg=文本过长，无法合成", err)
    }

    @Test
    fun `audio response is not mistaken for error`() {
        val client = VivoTtsClient()
        assertEquals(null, client.parseErrorResult(
            byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00),
        ))
    }

    @Test
    fun `accent maps to tts langType`() {
        assertEquals("en-GBR", Accent.UK.ttsLangType)
        assertEquals("en-USA", Accent.US.ttsLangType)
        assertEquals(1, Accent.UK.youdaoType)
        assertEquals(2, Accent.US.youdaoType)
    }

    @Test
    fun `default device id is a canonical decimal without leading zeros`() {
        // vivo 把 deviceid 当数值解析，冗余前导零会被拒(HTTP 400 "Leading zeroes not allowed")。
        // "0" 合法，但 "0000"、"0123" 这类非法。规范十进制往返需等于自身。
        val id = VivoTtsClient.DEFAULT_DEVICE_ID
        assertEquals(id, id.toLongOrNull()?.toString())
    }
}