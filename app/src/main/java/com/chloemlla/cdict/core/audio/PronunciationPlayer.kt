package com.chloemlla.cdict.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 文本朗读能力抽象，便于注入与测试；[PronunciationPlayer] 为其默认实现。 */
interface PronunciationSpeaker {
    fun speak(text: String)
    fun release()
}

/**
 * 发音播放器，三级回退：vivo TTS（默认）→ 有道静态音频 → 系统 TextToSpeech。
 * [accent] 决定音色语言（英式 en-GBR / 美式 en-USA）。
 */
class PronunciationPlayer(private val context: Context) : PronunciationSpeaker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val vivoClient = VivoTtsClient()

    override fun speak(text: String) = play(text, Accent.US)

    fun play(word: String, accent: Accent) {
        player?.release()
        player = null
        scope.launch { playVivo(word, accent) }
    }

    private suspend fun playVivo(word: String, accent: Accent) {
        val result = vivoClient.synthesize(word, langType = accent.ttsLangType)
        val audio = (result as? VivoTtsResult.Audio)?.bytes
        if (audio == null || audio.isEmpty()) {
            playYoudaoOrTts(word, accent)
            return
        }
        val tmp = File(context.cacheDir, "tts_${System.nanoTime()}.mp3")
        try {
            tmp.writeBytes(audio)
        } catch (e: Exception) {
            tmp.delete()
            playYoudaoOrTts(word, accent)
            return
        }
        player = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener { release(); player = null; tmp.delete() }
            setOnErrorListener { _, _, _ ->
                release(); player = null
                tmp.delete()
                playYoudaoOrTts(word, accent)
                true
            }
            try {
                setDataSource(tmp.path)
            } catch (e: Exception) {
                playYoudaoOrTts(word, accent)
            }
            prepareAsync()
        }
    }

    private fun playYoudaoOrTts(word: String, accent: Accent) {
        val fallback = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener { release() }
            setOnErrorListener { _, _, _ -> release(); speak(word, accent); true }
            try {
                setDataSource("https://dict.youdao.com/dictvoice?audio=${word.encodeUrl()}&type=${accent.youdaoType}")
            } catch (e: Exception) {
                speak(word, accent)
            }
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

    override fun release() {
        scope.cancel()
        player?.release()
        tts?.shutdown()
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

enum class Accent(val path: String, val youdaoType: Int, val ttsLangType: String) {
    UK("uk", 1, "en-GBR"),
    US("us", 2, "en-USA"),
}