package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.data.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyReviewTest {
    @Test
    fun partOfSpeechMapsConventionalPrefixes() {
        assertEquals("n", primaryPartOfSpeech("n. 苹果"))
        assertEquals("v", primaryPartOfSpeech("vt. 说"))
        assertEquals("v", primaryPartOfSpeech("vi. 跑"))
        assertEquals("adj", primaryPartOfSpeech("adj. 好的"))
        assertEquals("adv", primaryPartOfSpeech("adv. 很快地"))
        assertEquals("", primaryPartOfSpeech("苹果"))
        assertEquals("", primaryPartOfSpeech("  "))
    }

    @Test
    fun distractorsAreDedupedExcludeCorrectAndSharePosWhenAvailable() {
        val correct = "n. 苹果"
        val pool = mutableListOf<WordEntity>()
        (0 until 20).forEach { pool.add(WordEntity(id = it.toLong(), word = "w$it", translation = "n. 词$it")) }
        pool.add(WordEntity(id = 1000, word = "dup", translation = correct))

        val distractors = buildReviewDistractors("n", pool, correct)

        assertEquals(3, distractors.size)
        assertTrue("distractors must not duplicate the correct option", distractors.none { it == correct })
        assertEquals("distractors must be unique", distractors.size, distractors.distinct().size)
        assertTrue("distractors should share the part of speech", distractors.all { it.startsWith("n.") })
    }

    @Test
    fun distractorsFallBackToAnyTranslationsWhenPosPoolIsEmpty() {
        val correct = "n. 苹果"
        // Nothing shares the noun part of speech; every entry is a verb.
        val pool = (0 until 10).map { WordEntity(id = it.toLong(), word = "w$it", translation = "v. 动词$it") }
        val distractors = buildReviewDistractors("n", pool, correct)
        assertEquals(3, distractors.size)
        assertTrue(distractors.none { it == correct })
    }
}