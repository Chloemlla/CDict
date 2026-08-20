package com.chloemlla.cdict.core.net

import kotlinx.coroutines.runBlocking
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

    @Test
    fun `supporters are parsed trimmed and blank entries dropped`() {
        val outcome = client.parseChannels(
            response(
                200,
                """{"channels":[{"id":"alipay","name":"支付宝"}],"supporters":["  阿伟 ","",  "小明"]}""",
            ),
        )
        val info = (outcome as DonationOutcome.Success).info
        assertEquals(listOf("阿伟", "小明"), info.supporters)
    }

    @Test
    fun `missing supporters field yields an empty list`() {
        val outcome = client.parseChannels(
            response(200, """{"channels":[{"id":"alipay","name":"支付宝"}]}"""),
        )
        assertEquals(emptyList<String>(), (outcome as DonationOutcome.Success).info.supporters)
    }

    @Test
    fun `claim response carries the duplicated flag and server message`() {
        val outcome = client.parseClaim(
            response(200, """{"success":true,"duplicated":true,"message":"已提交过了"}"""),
        )
        val accepted = outcome as DonationClaimOutcome.Accepted
        assertTrue(accepted.duplicated)
        assertEquals("已提交过了", accepted.message)
    }

    @Test
    fun `claim error body is surfaced instead of a generic failure`() {
        val outcome = client.parseClaim(response(400, """{"error":"交易号不合法"}"""))
        assertEquals("交易号不合法", (outcome as DonationClaimOutcome.Rejected).message)
    }

    @Test
    fun `malformed transaction id is rejected before any request is sent`() = runBlocking {
        var called = false
        val offline = DonationClient(
            baseUrl = "https://tts.chloemlla.com",
            postTransport = { _, _ ->
                called = true
                response(200, """{"success":true}""")
            },
        )
        val outcome = offline.submitClaim("abc", "阿伟")
        assertTrue(outcome is DonationClaimOutcome.Rejected)
        assertEquals(false, called)
    }

    @Test
    fun `claim posts trimmed values to the claim endpoint`() = runBlocking {
        var seenUrl = ""
        var seenBody = ""
        val posting = DonationClient(
            baseUrl = "https://tts.chloemlla.com",
            postTransport = { url, body ->
                seenUrl = url
                seenBody = body
                response(200, """{"success":true,"duplicated":false,"message":"已提交"}""")
            },
        )
        val outcome = posting.submitClaim("  2026082012345678  ", "  阿伟  ")
        assertEquals("https://tts.chloemlla.com/api/cdict/donate/claim", seenUrl)
        assertTrue(seenBody.contains("\"transactionId\":\"2026082012345678\""))
        assertTrue(seenBody.contains("阿伟"))
        assertEquals(false, (outcome as DonationClaimOutcome.Accepted).duplicated)
    }
}
