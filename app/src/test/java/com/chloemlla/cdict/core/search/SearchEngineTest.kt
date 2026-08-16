package com.chloemlla.cdict.core.search

import com.chloemlla.cdict.core.data.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEngineTest {

    private fun word(id: Long, text: String, group: Int = 3, frequency: Int = 0) =
        WordEntity(id = id, word = text, frequencyGroup = group, frequency = frequency)

    @Test
    fun `exact match ranks before prefix match ranks before loose match`() {
        val words = listOf(
            word(2, "application", group = 1),
            word(1, "apple", group = 1),
            word(3, "apply", group = 5),
        )
        val ranked = SearchEngine.reorderForSearch("apple", words)
        assertEquals("apple", ranked[0].word)
        assertEquals("application", ranked[1].word)
        assertEquals("apply", ranked[2].word)
    }

    @Test
    fun `exact match is case insensitive`() {
        val words = listOf(word(1, "Apple"))
        assertEquals("Apple", SearchEngine.reorderForSearch("apple", words)[0].word)
    }

    @Test
    fun `single element query returns as-is`() {
        val words = listOf(word(1, "apple"))
        assertEquals(words, SearchEngine.reorderForSearch("apple", words))
    }

    @Test
    fun `suggests a word within edit distance two`() {
        val pool = listOf(word(1, "definite"), word(2, "definition"), word(3, "definitely"))
        val suggestion = SearchEngine.suggest("definit", pool)
        assertEquals("definite", suggestion?.word)
    }

    @Test
    fun `suggests an orthographic typo`() {
        val pool = listOf(word(1, "receive"), word(2, "believer"))
        assertEquals("receive", SearchEngine.suggest("recieve", pool)?.word)
    }

    @Test
    fun `returns null when nothing is close enough`() {
        val pool = listOf(word(1, "apple"), word(2, "banana"))
        assertNull(SearchEngine.suggest("zzzzzqqqq", pool))
    }

    @Test
    fun `blank query yields no suggestion`() {
        assertNull(SearchEngine.suggest("   ", listOf(word(1, "apple"))))
    }

    @Test
    fun `levenshtein distance basics`() {
        assertEquals(0, SearchEngine.levenshtein("cat", "cat"))
        assertEquals(1, SearchEngine.levenshtein("cat", "cut"))
        assertEquals(1, SearchEngine.levenshtein("cat", "cats"))
    }
}