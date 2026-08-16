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
        val target = WordEntity(id = 1, word = "apple", translation = "n. 苹果", frequencyGroup = 3)
        val pool = mutableListOf<WordEntity>()
        (0 until 20).forEach { pool.add(WordEntity(id = it.toLong() + 2, word = "w$it", translation = "n. 词$it", frequencyGroup = 3)) }
        pool.add(WordEntity(id = 1000, word = "dup", translation = "n. 苹果", frequencyGroup = 3))

        val distractors = buildReviewDistractors(target, pool)

        assertEquals(3, distractors.size)
        assertTrue("distractors must not duplicate the correct option", distractors.none { it == "n. 苹果" })
        assertEquals("distractors must be unique", distractors.size, distractors.distinct().size)
        assertTrue("distractors should share the part of speech", distractors.all { it.startsWith("n.") })
    }

    @Test
    fun semanticFilterDropsNearSynonymsInFavourOfUnrelatedDistractors() {
        val target = WordEntity(id = 1, word = "apple", translation = "n. 苹果", frequencyGroup = 3)
        // Identical-sense candidates (semantic similarity 1.0 > 0.65) saturate the front of
        // the pool; only genuinely unrelated nouns must survive the filter.
        val pool = mutableListOf<WordEntity>()
        (0 until 15).forEach { pool.add(WordEntity(id = it.toLong() + 2, word = "apple$it", translation = "n. 苹果", frequencyGroup = 3)) }
        (0 until 15).forEach { pool.add(WordEntity(id = it.toLong() + 100, word = "house$it", translation = "n. 房子$it", frequencyGroup = 2)) }

        val distractors = buildReviewDistractors(target, pool)

        assertEquals(3, distractors.size)
        assertTrue("near-synonym distractors must be filtered out", distractors.none { it.contains("苹果") })
        assertTrue("unrelated distractors survive", distractors.all { it.startsWith("n. 房子") })
    }

    @Test
    fun distractorsFallBackToAnyTranslationsWhenPosPoolIsEmpty() {
        val target = WordEntity(id = 1, word = "apple", translation = "n. 苹果", frequencyGroup = 3)
        // Nothing shares the noun part of speech; every entry is a verb.
        val pool = (0 until 10).map { WordEntity(id = it.toLong(), word = "w$it", translation = "v. 动词$it", frequencyGroup = 3) }
        val distractors = buildReviewDistractors(target, pool)
        assertEquals(3, distractors.size)
        assertTrue(distractors.none { it == "n. 苹果" })
    }

    @Test
    fun semanticSimilaritySeparatesSameSenseFromUnrelated() {
        assertTrue(semanticSimilarity("n. 苹果", "n. 苹果") > 0.65f)
        assertTrue(semanticSimilarity("n. 苹果", "n. 梨") < 0.65f)
        assertTrue(semanticSimilarity("v. 跑", "v. 飞") < 0.65f)
    }

    @Test
    fun levenshteinScoresOrthographicProximity() {
        assertEquals(0, levenshtein("apple", "apple"))
        assertEquals(1, levenshtein("apple", "apples"))
        assertEquals(1, levenshtein("cat", "cut"))
        assertTrue(levenshtein("house", "rocket") > 2)
    }
}