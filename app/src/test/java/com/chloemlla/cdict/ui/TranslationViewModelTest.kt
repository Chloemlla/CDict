package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.translate.HttpResponse
import com.chloemlla.cdict.core.translate.TranslationCache
import com.chloemlla.cdict.core.translate.TranslationCacheKey
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationResult
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun okClient(vararg lines: String) = VivoTranslationClient(
        transport = { _, _, _ ->
            HttpResponse(
                200,
                """{"retcode":0,"code":0,"data":{"translation":"${lines.joinToString("\\n")}","from":"en","to":"zh-CHS"}}""",
            )
        },
    )

    @Test
    fun `blank query does not call transport`() = runTest {
        var called = false
        val client = VivoTranslationClient(transport = { _, _, _ ->
            called = true
            HttpResponse(200, "{}")
        })
        val vm = TranslationViewModel(client)
        vm.onQueryChange("   ")
        vm.translate()
        advanceUntilIdle()
        assertFalse(called)
        assertEquals(TranslationUiState.Idle, vm.state.value)
    }

    @Test
    fun `successful translate updates state to success`() = runTest {
        val vm = TranslationViewModel(okClient("你好"))
        vm.onQueryChange("hello")
        vm.translate()
        advanceUntilIdle()
        val success = vm.state.value as TranslationUiState.Success
        assertEquals(listOf("你好"), success.result.translations)
        assertEquals("en", success.result.from)
    }

    @Test
    fun `state is translating while request in flight`() = runTest {
        val client = VivoTranslationClient(transport = { _, _, _ ->
            delay(1000)
            HttpResponse(200, """{"retcode":0,"code":0,"data":{"translation":"ok"}}""")
        })
        val vm = TranslationViewModel(client)
        vm.onQueryChange("hello")
        vm.translate()
        runCurrent()
        assertEquals(TranslationUiState.Translating, vm.state.value)
        advanceUntilIdle()
        assertTrue(vm.state.value is TranslationUiState.Success)
    }

    @Test
    fun `failed translate updates state to failure`() = runTest {
        val client = VivoTranslationClient(transport = { _, _, _ ->
            HttpResponse(200, """{"retcode":0,"code":71001,"msg":"bad param"}""")
        })
        val vm = TranslationViewModel(client)
        vm.onQueryChange("hello")
        vm.translate()
        advanceUntilIdle()
        val failure = vm.state.value as TranslationUiState.Failure
        assertTrue(failure.message.contains("71001"))
    }

    @Test
    fun `query state reflects input`() = runTest {
        val vm = TranslationViewModel(okClient("ok"))
        vm.onQueryChange("你好 world")
        assertEquals("你好 world", vm.query.value)
    }

    @Test
    fun `loadSupportedLanguages populates supportedLanguages`() = runTest {
        var getCalled = false
        val client = VivoTranslationClient(getTransport = {
            getCalled = true
            HttpResponse(200, """["en","zh-CHS","ja"]""")
        })
        val vm = TranslationViewModel(client)
        vm.loadSupportedLanguages()
        advanceUntilIdle()
        assertTrue(getCalled)
        assertEquals(listOf("en", "ja", "zh-chs"), vm.supportedLanguages.value)
    }

    @Test
    fun `constructing viewmodel does not invoke transport`() = runTest {
        var called = false
        val client = VivoTranslationClient(
            transport = { _, _, _ -> called = true; HttpResponse(200, "{}") },
            getTransport = { called = true; HttpResponse(200, "[]") },
        )
        val vm = TranslationViewModel(client)
        advanceUntilIdle()
        assertFalse(called)
        assertEquals(emptyList<String>(), vm.supportedLanguages.value)
    }

    @Test
    fun `cache hit renders without touching transport`() = runTest {
        var called = false
        val client = VivoTranslationClient(transport = { _, _, _ ->
            called = true
            HttpResponse(200, "{}")
        })
        val cached = TranslationResult(listOf("来自缓存"), "en", "zh-CHS", null)
        val hitCache = object : TranslationCache {
            override suspend fun get(key: String) = cached
            override suspend fun put(
                key: String,
                sourceText: String,
                direction: TranslationDirection,
                result: TranslationResult,
            ) = Unit

            override suspend fun markFavorite(key: String, favorite: Boolean) = Unit
        }
        val vm = TranslationViewModel(client, hitCache)
        vm.onQueryChange("hello")
        vm.translate()
        advanceUntilIdle()
        assertFalse(called)
        val success = vm.state.value as TranslationUiState.Success
        assertEquals(listOf("来自缓存"), success.result.translations)
    }

    @Test
    fun `network result is stored into cache`() = runTest {
        var storedKey: String? = null
        var storedResult: TranslationResult? = null
        val recordingCache = object : TranslationCache {
            override suspend fun get(key: String) = null
            override suspend fun put(
                key: String,
                sourceText: String,
                direction: TranslationDirection,
                result: TranslationResult,
            ) {
                storedKey = key
                storedResult = result
            }

            override suspend fun markFavorite(key: String, favorite: Boolean) = Unit
        }
        val vm = TranslationViewModel(okClient("你好"), recordingCache)
        vm.onQueryChange("hello")
        vm.translate()
        advanceUntilIdle()
        val success = vm.state.value as TranslationUiState.Success
        assertEquals(listOf("你好"), success.result.translations)
        assertNotNull(storedResult)
        assertEquals(
            TranslationCacheKey.of("hello", TranslationDirection.AUTO_TO_ZH),
            storedKey,
        )
    }
}
