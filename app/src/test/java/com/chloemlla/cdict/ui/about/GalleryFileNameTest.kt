package com.chloemlla.cdict.ui.about

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryFileNameTest {

    private val epochInUtc: Long = 0L

    @Test
    fun `file name keeps the base name and ends with png`() {
        val name = galleryFileName("CDict-alipay", epochInUtc)
        assertTrue(name, name.startsWith("CDict-alipay-"))
        assertTrue(name, name.endsWith(".png"))
    }

    @Test
    fun `path separators and traversal characters are stripped`() {
        val name = galleryFileName("../../etc/pass wd", epochInUtc)
        assertFalse(name, name.contains('/'))
        assertFalse(name, name.contains(".."))
        assertFalse(name, name.contains(' '))
        assertTrue(name, name.startsWith("etc-pass-wd-"))
    }

    @Test
    fun `blank base name falls back to a usable name`() {
        val name = galleryFileName("///", epochInUtc)
        assertTrue(name, name.startsWith("image-"))
    }

    @Test
    fun `timestamp is formatted as sortable date and time`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("CDict-wechat-19700101-000000.png", galleryFileName("CDict-wechat", 0L))
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
