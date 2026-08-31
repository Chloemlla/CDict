package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.audio.PronunciationSpeaker
import com.chloemlla.cdict.core.translate.HttpResponse
import com.chloemlla.cdict.core.translate.VivoTranslationClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhraseSpeechViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSpeaker : PronunciationSpeaker {
        var spoken: String? = null
        var stopped = false
        override var onCompletion: (() -> Unit)? = null
        override fun speak(text: String) {
            spoken = text
        }

        override fun stop() {
            stopped = true
            onCompletion = null
        }

        override fun release() {}
    }

    private fun okClient(zh: String) = VivoTranslationClient(
        transport = { _, _, _ ->
            HttpResponse(
                200,
                """{"retcode":0,"code":0,"data":{"translation":"$zh","from":"en","to":"zh-CHS"}}""",
            )
        },
    )

    @Test
    fun `translate marks loading then done`() = runTest {
        var calls = 0
        val client = VivoTranslationClient(transport = { _, _, _ ->
            calls++
            delay(1000)
            HttpResponse(200, """{"retcode":0,"code":0,"data":{"translation":"释义","from":"en","to":"zh-CHS"}}""")
        })
        val vm = PhraseSpeechViewModel(client, FakeSpeaker())
        vm.translate("definition")
        runCurrent()
        assertEquals(PhraseUiState.Loading, vm.states.value["definition"])
        advanceUntilIdle()
        assertEquals(PhraseUiState.Done("释义"), vm.states.value["definition"])
        assertEquals(1, calls)
    }

    @Test
    fun `repeated translate for same phrase is idempotent`() = runTest {
        var calls = 0
        val client = VivoTranslationClient(transport = { _, _, _ ->
            calls++
            HttpResponse(200, """{"retcode":0,"code":0,"data":{"translation":"句","from":"en","to":"zh-CHS"}}""")
        })
        val vm = PhraseSpeechViewModel(client, FakeSpeaker())
        vm.translate("a sentence")
        vm.translate("a sentence")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(PhraseUiState.Done("句"), vm.states.value["a sentence"])
    }

    @Test
    fun `translate failure yields error`() = runTest {
        val client = VivoTranslationClient(transport = { _, _, _ ->
            HttpResponse(200, """{"retcode":0,"code":71001,"msg":"bad param"}""")
        })
        val vm = PhraseSpeechViewModel(client, FakeSpeaker())
        vm.translate("boom")
        advanceUntilIdle()
        assertTrue(vm.states.value["boom"] is PhraseUiState.Error)
    }

    @Test
    fun `blank phrase is ignored`() = runTest {
        var called = false
        val client = VivoTranslationClient(transport = { _, _, _ ->
            called = true
            HttpResponse(200, "{}")
        })
        val vm = PhraseSpeechViewModel(client, FakeSpeaker())
        vm.translate("   ")
        advanceUntilIdle()
        assertTrue(vm.states.value.isEmpty())
    }

    @Test
    fun `speak delegates to speaker`() = runTest {
        val speaker = FakeSpeaker()
        val vm = PhraseSpeechViewModel(okClient("ok"), speaker)
        vm.speak("hello")
        assertEquals("hello", speaker.spoken)
    }

    @Test
    fun `speak sets speaking key and same text toggles to stop`() = runTest {
        val speaker = FakeSpeaker()
        val vm = PhraseSpeechViewModel(okClient("ok"), speaker)
        vm.speak("hello")
        assertEquals("hello", vm.speakingKey.value)
        vm.speak("hello")
        assertTrue(speaker.stopped)
        assertEquals(null, vm.speakingKey.value)
    }

    @Test
    fun `speak another text switches to the new text`() = runTest {
        val speaker = FakeSpeaker()
        val vm = PhraseSpeechViewModel(okClient("ok"), speaker)
        vm.speak("hello")
        vm.speak("world")
        assertEquals("world", vm.speakingKey.value)
        assertEquals("world", speaker.spoken)
    }

    @Test
    fun `completion clears speaking key`() = runTest {
        val speaker = FakeSpeaker()
        val vm = PhraseSpeechViewModel(okClient("ok"), speaker)
        vm.speak("hello")
        assertEquals("hello", vm.speakingKey.value)
        speaker.onCompletion?.invoke()
        assertEquals(null, vm.speakingKey.value)
    }

    @Test
    fun `construction does not invoke transport`() = runTest {
        var called = false
        val client = VivoTranslationClient(
            transport = { _, _, _ -> called = true; HttpResponse(200, "{}") },
        )
        val vm = PhraseSpeechViewModel(client, FakeSpeaker())
        advanceUntilIdle()
        assertEquals(emptyMap<String, PhraseUiState>(), vm.states.value)
    }
}