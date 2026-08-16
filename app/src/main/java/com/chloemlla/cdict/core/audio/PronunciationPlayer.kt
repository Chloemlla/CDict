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
 * 发音播放器，三级回退：有道静态音频（默认，优先；句子按词拆读）→ vivo TTS → 系统 TextToSpeech。
 * [accent] 决定音色语言（英式 en-GBR / 美式 en-USA）。
 */
class PronunciationPlayer(private val context: Context) : PronunciationSpeaker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val vivoClient = VivoTtsClient()

    override fun speak(text: String) = play(text, Accent.US)

    fun play(word: String, accent: Accent) {
        releasePlayer()
        scope.launch { playYoudao(word, accent) }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    /** 第一级：有道。先整句试一次；有道只认单词时，按空格拆词逐词朗读，仍失败才回退 vivo→系统 TTS。 */
    private suspend fun playYoudao(word: String, accent: Accent) {
        val media = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener { releasePlayer() }
            setOnErrorListener { _, _, _ ->
                releasePlayer()
                playYoudaoSentenceFallback(word, accent)
                true
            }
        }
        player = media
        try {
            media.setDataSource("https://dict.youdao.com/dictvoice?audio=${word.encodeUrl()}&type=${accent.youdaoType}")
            media.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            playYoudaoSentenceFallback(word, accent)
        }
    }

    /** 有道整句失败：多词拆词逐词读；单词/失败回退 vivo→系统 TTS。 */
    private fun playYoudaoSentenceFallback(word: String, accent: Accent) {
        val words = word.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 1) playYoudaoWords(words, 0, accent) else scope.launch { playVivoFallback(word, accent) }
    }

    private fun playYoudaoWords(words: List<String>, index: Int, accent: Accent) {
        if (index >= words.size) {
            releasePlayer()
            return
        }
        val media = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener {
                releasePlayer()
                playYoudaoWords(words, index + 1, accent)
            }
            setOnErrorListener { _, _, _ ->
                releasePlayer()
                scope.launch { playVivoFallback(words.joinToString(" "), accent) }
                true
            }
            try {
                setDataSource("https://dict.youdao.com/dictvoice?audio=${words[index].encodeUrl()}&type=${accent.youdaoType}")
            } catch (e: Exception) {
                releasePlayer()
                scope.launch { playVivoFallback(words.joinToString(" "), accent) }
            }
            prepareAsync()
        }
        player = media
    }

    /** 第二级：vivo TTS。失败时落到系统 TextToSpeech。 */
    private suspend fun playVivoFallback(word: String, accent: Accent) {
        val result = vivoClient.synthesize(word, langType = accent.ttsLangType)
        val audio = (result as? VivoTtsResult.Audio)?.bytes
        if (audio == null || audio.isEmpty()) {
            speak(word, accent)
            return
        }
        val (bytes, ext) = when {
            looksLikeWav(audio) -> audio to "wav"
            looksLikeMp3(audio) -> audio to "mp3"
            // vivo `/fy/tts` 的 auf=audio/L16;rate=16000 会返回无容器 PCM,MediaPlayer 无法直接播,
            // 补一个 WAV 头让它可播放;若将来返回其它已封装格式则走上面的检测分支,此处是兜底。
            else -> wavWrap(audio) to "wav"
        }
        val tmp = File(context.cacheDir, "tts_${System.nanoTime()}.$ext")
        try {
            tmp.writeBytes(bytes)
        } catch (e: Exception) {
            tmp.delete()
            speak(word, accent)
            return
        }
        player = MediaPlayer().apply {
            setOnPreparedListener { start() }
            setOnCompletionListener { releasePlayer(); tmp.delete() }
            setOnErrorListener { _, _, _ ->
                releasePlayer()
                tmp.delete()
                speak(word, accent)
                true
            }
            try {
                setDataSource(tmp.path)
            } catch (e: Exception) {
                speak(word, accent)
            }
            prepareAsync()
        }
    }

    private fun looksLikeMp3(bytes: ByteArray): Boolean {
        // "ID3" 标签头
        if (bytes.size >= 3 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()) return true
        // MPEG 音频帧同步 0xFFE/0xFFF
        return bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0
    }

    private fun looksLikeWav(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()

    /** 给无容器的 16-bit PCM(单声道 16kHz)补 WAV 头,以便 MediaPlayer 播放。 */
    private fun wavWrap(pcm: ByteArray, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val dataSize = pcm.size
        val out = ByteArray(44 + dataSize)
        fun putStr(offset: Int, s: String) = s.forEachIndexed { i, c -> out[offset + i] = c.code.toByte() }
        fun put32(offset: Int, v: Int) {
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = (v shr 8 and 0xFF).toByte()
            out[offset + 2] = (v shr 16 and 0xFF).toByte()
            out[offset + 3] = (v shr 24 and 0xFF).toByte()
        }
        fun put16(offset: Int, v: Int) {
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = (v shr 8 and 0xFF).toByte()
        }
        putStr(0, "RIFF"); put32(4, 36 + dataSize); putStr(8, "WAVE")
        putStr(12, "fmt "); put32(16, 16)
        put16(20, 1); put16(22, channels); put32(24, sampleRate); put32(28, byteRate)
        put16(32, blockAlign); put16(34, bitsPerSample)
        putStr(36, "data"); put32(40, dataSize)
        pcm.copyInto(out, 44)
        return out
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