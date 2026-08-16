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
        val file = cache.put("apple", Accent.US, bytes)
        assertTrue(file.isFile)
        assertNotNull(cache.find("apple", Accent.US))
    }

    @Test
    fun `find persists the byte content`() {
        val cache = SpeechAudioCache(context)
        val bytes = "RIFF".toByteArray()
        cache.put("banana", Accent.UK, bytes)
        assertTrue(cache.find("banana", Accent.UK)!!.length() == bytes.size.toLong())
    }

    @Test
    fun `accent and text form distinct cache keys`() {
        val cache = SpeechAudioCache(context)
        cache.put("apple", Accent.US, ByteArray(2))
        assertNull(cache.find("apple", Accent.UK)) // different accent => different file
    }
}