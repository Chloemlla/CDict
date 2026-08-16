package com.chloemlla.cdict.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryLruCacheTest {

    @Test
    fun `evicts least recently used when full`() {
        val lru = MemoryLruCache<String, String>(2)
        lru.put("a", "1")
        lru.put("b", "2")
        assertEquals("1", lru.get("a"))
        lru.put("c", "3")
        assertNull(lru.get("b"))
        assertEquals("3", lru.get("c"))
        assertEquals("1", lru.get("a"))
    }

    @Test
    fun `read refreshes recency so hot key survives`() {
        val lru = MemoryLruCache<String, String>(2)
        lru.put("a", "1")
        lru.put("b", "2")
        lru.get("a")
        lru.put("c", "3")
        assertNull(lru.get("b"))
        assertEquals("1", lru.get("a"))
    }

    @Test
    fun `size never exceeds cap`() {
        val lru = MemoryLruCache<String, String>(1)
        lru.put("x", "1")
        lru.put("y", "2")
        assertEquals(1, lru.size())
        assertNull(lru.get("x"))
        assertEquals("2", lru.get("y"))
    }
}