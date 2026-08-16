package com.chloemlla.cdict.core.audio

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechAudioCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `put then find returns the cached file`() {
        val cache = SpeechAudioCache(context)
        val bytes = ByteArray(256) { 1 }
        val file = cache.put("apple", Accent.US, "vivo", bytes)
        assertTrue(file.isFile)
        assertNotNull(cache.find("apple", Accent.US, "vivo"))
    }

    @Test
    fun `find persists the byte content`() {
        val cache = SpeechAudioCache(context)
        val bytes = "RIFF".toByteArray()
        cache.put("banana", Accent.UK, "youdao", bytes)
        assertTrue(cache.find("banana", Accent.UK, "youdao")!!.length() == bytes.size.toLong())
    }

    @Test
    fun `accent and text form distinct cache keys`() {
        val cache = SpeechAudioCache(context)
        cache.put("apple", Accent.US, "vivo", ByteArray(2))
        assertNull(cache.find("apple", Accent.UK, "vivo")) // different accent => different file
    }

    @Test
    fun `source keeps pronunciation tiers in separate namespaces`() {
        val cache = SpeechAudioCache(context)
        cache.put("apple", Accent.US, "vivo", ByteArray(2))
        cache.put("apple", Accent.US, "youdao", ByteArray(2))
        assertNotNull(cache.find("apple", Accent.US, "vivo"))
        assertNotNull(cache.find("apple", Accent.US, "youdao"))
        assertTrue(cache.find("apple", Accent.US, "vivo")!!.length() == 2L)
        assertTrue(cache.find("apple", Accent.US, "youdao")!!.length() == 2L)
    }
}
