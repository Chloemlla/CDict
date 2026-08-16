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