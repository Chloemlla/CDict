package com.chloemlla.cdict.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationClientTest {

    private val client = DonationClient(baseUrl = "https://tts.chloemlla.com")

    private fun response(status: Int, body: String) =
        DonationHttpResponse(status, body.toByteArray(Charsets.UTF_8))

    @Test
    fun `image url is always built from the backend base url`() {
        assertEquals(
            "https://tts.chloemlla.com/api/cdict/donate/alipay",
            client.imageUrl("alipay"),
        )
    }

    @Test
    fun `channels are parsed and absolute urls in the payload are ignored`() {
        val outcome = client.parseChannels(
            response(
                200,
                """
                {"success":true,"notice":"自愿赞赏","channels":[
                  {"id":"alipay","name":"支付宝","hint":"扫一扫","imageUrl":"https://evil.example/x.png"},
                  {"id":"wechat","name":"微信"}
                ]}
                """.trimIndent(),
            ),
        )
        val info = (outcome as DonationOutcome.Success).info
        assertEquals("自愿赞赏", info.notice)
        assertEquals(listOf("alipay", "wechat"), info.channels.map { it.id })
        assertEquals("扫一扫", info.channels[0].hint)
        assertEquals(null, info.channels[1].hint)
    }

    @Test
    fun `channel ids that could escape the donate path are dropped`() {
        val outcome = client.parseChannels(
            response(
                200,
                """{"channels":[{"id":"../../etc/passwd","name":"x"},{"id":"AliPay","name":"y"}]}""",
            ),
        )
        assertTrue(outcome is DonationOutcome.Failure)
    }

    @Test
    fun `non 2xx is reported as failure instead of empty success`() {
        val outcome = client.parseChannels(response(503, """{"error":"donation disabled"}"""))
        val message = (outcome as DonationOutcome.Failure).message
        assertTrue(message.contains("503"))
        assertTrue(message.contains("donation disabled"))
    }

    @Test
    fun `empty channel list is a failure`() {
        val outcome = client.parseChannels(response(200, """{"success":true,"channels":[]}"""))
        assertTrue(outcome is DonationOutcome.Failure)
    }
}
