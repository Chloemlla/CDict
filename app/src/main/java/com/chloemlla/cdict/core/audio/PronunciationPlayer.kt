package com.chloemlla.cdict.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 文本朗读能力抽象，便于注入与测试；[PronunciationPlayer] 为其默认实现。 */
interface PronunciationSpeaker {
    fun speak(text: String)
    fun release()
}

/**
 * 发音播放器，三级回退：有道静态音频（默认，优先）→ vivo TTS → 系统 TextToSpeech。
 * 有道只保证单词；整句交由 vivo / 系统 TTS 整句朗读，绝不逐词拆读。
 * [accent] 决定音色语言（英式 en-GBR / 美式 en-USA）。
 *
 * 并发安全：每次 [play] 都会让之前的播放流水线失效（generation 递增 + 取消旧 job），
 * MediaPlayer 回调只作用于它自己的播放器（`player === media`），避免旧回调误杀新播放；
 * 同词同音色的下载经单飞（single-flight）共享一次 HTTP 请求，避免 prefetch 与 play 重复下载。
 */
class PronunciationPlayer(private val context: Context) : PronunciationSpeaker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var playJob: Job? = null
    private var playGeneration = 0
    private val vivoClient = VivoTtsClient()
    private val cache = SpeechAudioCache(context)

    /** 同 <音色>:<文本> 的进行中下载共享；只由 Main 线程访问。 */
    private val inFlight = mutableMapOf<String, Deferred<ByteArray?>>()

    override fun speak(text: String) = play(text, Accent.US)

    fun play(word: String, accent: Accent) {
        playGeneration++
        playJob?.cancel()
        releasePlayer()
        playJob = scope.launch { playYoudao(playGeneration, word, accent) }
    }

    /**
     * Pre-fetch (PRD §3.4): pull a word's Youdao audio into the disk LRU cache in the
     * background so an imminent play hits the cached file instead of the network. The
     * origin tier is the Youdao accent (UK/US) so cache keys align with real playback.
     */
    fun prefetch(word: String, accent: Accent) {
        scope.launch {
            if (cache.find(word, accent) == null) {
                val bytes = downloadYoudao(word, accent)
                if (bytes != null && bytes.isNotEmpty()) cache.put(word, accent, bytes)
            }
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    /** 第一级：有道。命中磁盘 LRU 缓存则直接播放缓存文件；未命中下载字节并写缓存。 */
    private suspend fun playYoudao(generation: Int, word: String, accent: Accent) {
        if (generation != playGeneration) return
        val cached = cache.find(word, accent)
        if (cached != null) {
            playFile(generation, cached, word, accent)
            return
        }
        val bytes = downloadYoudao(word, accent)
        if (bytes == null || bytes.isEmpty()) {
            playYoudaoSentenceFallback(generation, word, accent)
            return
        }
        val file = cache.put(word, accent, bytes)
        if (generation == playGeneration) playFile(generation, file, word, accent)
    }

    /** 单飞下载：并发请求同一 <音色>:<文本> 只发一次 HTTP，其余复用结果。 */
    private suspend fun downloadYoudao(word: String, accent: Accent): ByteArray? {
        val key = "${accent.name}:$word"
        inFlight[key]?.let { return it.await() }
        val deferred = scope.async(Dispatchers.IO) { fetchYoudao(word, accent) }
        inFlight[key] = deferred
        try {
            return deferred.await()
        } finally {
            inFlight.remove(key)
        }
    }

    /** 有道下载。只接受 200 且 content-type 为 audio 类型、或缺失 content-type 但可识别音频容器的响应；错误页/空体返回 null 触发回退。 */
    private fun fetchYoudao(word: String, accent: Accent): ByteArray? = try {
        val conn = URL("https://dict.youdao.com/dictvoice?audio=${word.encodeUrl()}&type=${accent.youdaoType}")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode != 200) return null
            val type = conn.contentType
            val audioType = type != null && type.startsWith("audio/", ignoreCase = true)
            if (type != null && type.isNotBlank() && !audioType) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            if (!audioType && !looksLikeAudio(bytes)) return null
            bytes
        } finally {
            conn.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun playFile(generation: Int, file: File, word: String, accent: Accent) {
        val media = MediaPlayer().apply {
            setOnPreparedListener {
                if (generation == playGeneration && player === this) start()
            }
            setOnCompletionListener {
                if (player === this) releasePlayer()
            }
            setOnErrorListener { _, _, _ ->
                if (player === this) {
                    releasePlayer()
                    file.delete()
                    playYoudaoSentenceFallback(generation, word, accent)
                }
                true
            }
        }
        player = media
        try {
            media.setDataSource(file.path)
            media.prepareAsync()
        } catch (e: Exception) {
            if (player === media) releasePlayer()
            file.delete()
            playYoudaoSentenceFallback(generation, word, accent)
        }
    }

    /** 有道整句失败：不再逐词拆读（那会按词打断句子），直接把整句交给后备 TTS（vivo → 系统）整句朗读。 */
    private fun playYoudaoSentenceFallback(generation: Int, word: String, accent: Accent) {
        if (generation != playGeneration) return
        scope.launch { playVivoFallback(generation, word, accent) }
    }

    /** 第二级：vivo TTS。失败时落到系统 TextToSpeech。 */
    private suspend fun playVivoFallback(generation: Int, word: String, accent: Accent) {
        if (generation != playGeneration) return
        val result = vivoClient.synthesize(word, langType = accent.ttsLangType)
        if (generation != playGeneration) return
        val audio = (result as? VivoTtsResult.Audio)?.bytes
        if (audio == null || audio.isEmpty()) {
            if (generation == playGeneration) speak(generation, word, accent)
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
            if (generation == playGeneration) speak(generation, word, accent)
            return
        }
        val media = MediaPlayer().apply {
            setOnPreparedListener {
                if (generation == playGeneration && player === this) start()
            }
            setOnCompletionListener {
                if (player === this) {
                    releasePlayer()
                    tmp.delete()
                }
            }
            setOnErrorListener { _, _, _ ->
                if (player === this) {
                    releasePlayer()
                    tmp.delete()
                    if (generation == playGeneration) speak(generation, word, accent)
                }
                true
            }
        }
        player = media
        try {
            media.setDataSource(tmp.path)
        } catch (e: Exception) {
            if (player === media) releasePlayer()
            tmp.delete()
            if (generation == playGeneration) speak(generation, word, accent)
            return
        }
        if (generation == playGeneration) media.prepareAsync()
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

    private fun looksLikeAudio(bytes: ByteArray): Boolean = looksLikeMp3(bytes) || looksLikeWav(bytes)

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

    /** 系统 TextToSpeech 兜底。语言必须在引擎初始化成功后设置，否则初始化前 setLanguage 无效、UK/US 音色不生效。 */
    private fun speak(generation: Int, word: String, accent: Accent) {
        val locale = if (accent == Accent.UK) Locale.UK else Locale.US
        tts?.shutdown()
        var newTts: TextToSpeech? = null
        newTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS && generation == playGeneration && tts === newTts) {
                newTts?.language = locale
                newTts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, word)
            }
        }
        tts = newTts
    }

    override fun release() {
        scope.cancel()
        playJob = null
        player?.release()
        player = null
        tts?.shutdown()
        tts = null
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}

enum class Accent(val path: String, val youdaoType: Int, val ttsLangType: String) {
    UK("uk", 1, "en-GBR"),
    US("us", 2, "en-USA"),
}
