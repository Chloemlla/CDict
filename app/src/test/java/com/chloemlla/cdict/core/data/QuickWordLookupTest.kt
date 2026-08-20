package com.chloemlla.cdict.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickWordLookupTest {

    @Test
    fun `normalizes case and whitespace`() {
        assertEquals(listOf("resilient"), QuickWordLookup.candidates("  Resilient  "))
    }

    @Test
    fun `strips surrounding punctuation as a second candidate`() {
        assertEquals(listOf("\"resilient\"", "resilient"), QuickWordLookup.candidates("\"Resilient\""))
        assertEquals(listOf("resilient.", "resilient"), QuickWordLookup.candidates("resilient."))
    }

    @Test
    fun `keeps hyphen and apostrophe inside a headword`() {
        assertEquals(listOf("well-being"), QuickWordLookup.candidates("well-being"))
        assertEquals(listOf("don't"), QuickWordLookup.candidates("don't"))
    }

    @Test
    fun `rejects blank and overlong selections`() {
        assertTrue(QuickWordLookup.candidates("   ").isEmpty())
        assertTrue(QuickWordLookup.candidates("a".repeat(65)).isEmpty())
        assertEquals(listOf("a".repeat(64)), QuickWordLookup.candidates("a".repeat(64)))
    }
}
