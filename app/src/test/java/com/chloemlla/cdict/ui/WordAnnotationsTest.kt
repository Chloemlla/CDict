package com.chloemlla.cdict.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordAnnotationsTest {
    @Test
    fun `emotion color labels cover every schema enum value`() {
        assertEquals("褒义", emotionColorLabel("positive"))
        assertEquals("贬义", emotionColorLabel("negative"))
        assertEquals("中性", emotionColorLabel("neutral"))
        assertEquals("视语境", emotionColorLabel("context_dependent"))
    }

    @Test
    fun `register labels cover every schema enum value including neutral`() {
        assertEquals("学术", registerLabel("academic"))
        assertEquals("口语", registerLabel("spoken"))
        assertEquals("书面", registerLabel("written"))
        assertEquals("文学", registerLabel("literary"))
        assertEquals("非正式", registerLabel("informal"))
        assertEquals("中性", registerLabel("neutral"))
    }

    @Test
    fun `unrecognized enum values fall back without crashing`() {
        assertNull(emotionColorLabel("nonsense"))
        assertNull(emotionColorLabel(null))
        assertEquals("weird", registerLabel("weird"))
        assertNull(registerLabel(null))
    }
}
