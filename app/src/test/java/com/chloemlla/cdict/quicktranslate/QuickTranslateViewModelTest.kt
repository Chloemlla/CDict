package com.chloemlla.cdict.quicktranslate

import com.chloemlla.cdict.core.translate.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTranslateViewModelTest {

    @Test
    fun `normalize trims and caps source length`() {
        assertEquals("hello", QuickTranslateViewModel.normalizeSource("  hello \n"))
        assertEquals(
            QuickTranslateViewModel.MAX_SOURCE_LENGTH,
            QuickTranslateViewModel.normalizeSource("x".repeat(5_000)).length,
        )
    }

    @Test
    fun `chinese selection defaults to english output`() {
        assertEquals(
            TranslationDirection.AUTO_TO_EN,
            QuickTranslateViewModel.defaultDirection("坚韧"),
        )
        assertEquals(
            TranslationDirection.AUTO_TO_EN,
            QuickTranslateViewModel.defaultDirection("这个 word 很难"),
        )
    }

    @Test
    fun `latin selection defaults to chinese output`() {
        assertEquals(
            TranslationDirection.AUTO_TO_ZH,
            QuickTranslateViewModel.defaultDirection("resilient"),
        )
        assertEquals(
            TranslationDirection.AUTO_TO_ZH,
            QuickTranslateViewModel.defaultDirection("Stay hungry, stay foolish."),
        )
    }
}
