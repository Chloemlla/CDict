package com.chloemlla.cdict.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale

class PronunciationPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null

    fun play(word: String, accent: Accent) {
        player?.release()
        player = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener { release(); player = null }
            setOnErrorListener { _, _, _ -> release(); player = null; playYoudaoOrTts(word, accent); true }
            setDataSource(cdnUrl(word, accent))
            prepareAsync()
        }
    }

    private fun playYoudaoOrTts(word: String, accent: Accent) {
        val fallback = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener { release() }
            setOnErrorListener { _, _, _ -> release(); speak(word, accent); true }
            setDataSource("https://dict.youdao.com/dictvoice?audio=${word.encodeUrl()}&type=${accent.youdaoType}")
            prepareAsync()
        }
        player = fallback
    }

    private fun speak(word: String, accent: Accent) {
        val locale = if (accent == Accent.UK) Locale.UK else Locale.US
        tts?.shutdown()
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, word)
        }.also { it.language = locale }
    }

    fun release() {
        player?.release()
        tts?.shutdown()
    }

    private fun cdnUrl(word: String, accent: Accent) =
        "https://cdn.isdc.pages.dev/audio/${accent.path}/${word.encodeUrl()}.mp3"

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

enum class Accent(val path: String, val youdaoType: Int) { UK("uk", 1), US("us", 2) }
