package com.chloemlla.cdict.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
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
 * 发音播放器，三级回退：按设置以有道或 vivo TTS 为首选 → 另一在线来源 → 系统 TextToSpeech。
 * [accent] 决定音色语言（英式 en-GBR / 美式 en-USA）。
 *
 * 并发安全：每次 [play] 都会让之前的播放流水线失效（generation 递增 + 取消旧 job），
 * MediaPlayer 回调只作用于它自己的播放器（`player === media`），避免旧回调误杀新播放；
 * 同词同音色的下载经单飞（single-flight）共享一次 HTTP 请求，避免 prefetch 与 play 重复下载。
 * 磁盘 LRU 缓存按来源（vivo / 有道）分命名空间，两级音频互不覆盖。
 */
class PronunciationPlayer(private val context: Context) : PronunciationSpeaker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var playJob: Job? = null
    private var playGeneration = 0
    private val vivoClient = VivoTtsClient()
    private val cache = SpeechAudioCache(context)
    private val preferenceStore = context.applicationContext
        .getSharedPreferences("cdict_about", Context.MODE_PRIVATE)

    private val youdaoFirst: Boolean
        get() = preferenceStore.getBoolean("youdao_first", true)

    /** 同 <音色>:<文本> 的进行中下载共享；只由 Main 线程访问。 */
    private val inFlight = mutableMapOf<String, Deferred<YoudaoFetch>>()

    override fun speak(text: String) = play(text, Accent.US)

    fun stop() {
        playGeneration++
        playJob?.cancel()
        releasePlayer()
        tts?.stop()
    }

    fun play(word: String, accent: Accent) {
        playGeneration++
        playJob?.cancel()
        releasePlayer()
        playJob = scope.launch {
            if (youdaoFirst) {
                playYoudaoFirst(playGeneration, word, accent)
            } else {
                playVivoFirst(playGeneration, word, accent)
            }
        }
    }

    /**
     * Pre-fetch (PRD §3.4): pull a word's Youdao audio into the disk LRU cache in the
     * background so an imminent play hits the cached file instead of the network. The
     * origin tier is the Youdao accent (UK/US) so cache keys align with real playback.
     */
    fun prefetch(word: String, accent: Accent) {
        scope.launch {
            if (cache.find(word, accent, SOURCE_YOUDAO) == null) {
                val bytes = downloadYoudao(word, accent)
                if (bytes != null && bytes.isNotEmpty()) cache.put(word, accent, SOURCE_YOUDAO, bytes)
            }
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    /** vivo 优先时的第一级：命中缓存直接播；未命中合成并写缓存；失败落到有道。 */
    private suspend fun playVivoFirst(generation: Int, word: String, accent: Accent) {
        if (generation != playGeneration) return
        val cached = cache.find(word, accent, SOURCE_VIVO)
        if (cached != null) {
            playFile(generation, cached) {
                scope.launch { playYoudaoFallback(generation, word, accent, "vivo 缓存音频播放失败") }
            }
            return
        }
        val result = vivoClient.synthesize(word, langType = accent.ttsLangType)
        if (generation != playGeneration) return
        val vivoReason: String? = when (result) {
            is VivoTtsResult.Audio -> if (result.bytes.isEmpty()) "vivo 返回空音频" else null
            is VivoTtsResult.Error -> result.message
        }
        val audio = (result as? VivoTtsResult.Audio)?.bytes
        if (audio == null || audio.isEmpty()) {
            if (generation == playGeneration) {
                playYoudaoFallback(generation, word, accent, vivoReason ?: "vivo 无返回值")
            }
            return
        }
        val file = cache.put(word, accent, SOURCE_VIVO, preparePlayable(audio))
        if (generation == playGeneration) {
            playFile(generation, file) {
                scope.launch {
                    playYoudaoFallback(generation, word, accent, "vivo 合成音频播放失败")
                }
            }
        }
    }

    /** vivo 优先时的第二级：有道。失败后落到系统 TTS。 */
    private suspend fun playYoudaoFallback(
        generation: Int,
        word: String,
        accent: Accent,
        vivoReason: String,
    ) {
        if (generation != playGeneration) return
        val cached = cache.find(word, accent, SOURCE_YOUDAO)
        if (cached != null) {
            playFile(generation, cached) {
                scope.launch {
                    speak(generation, word, accent, vivoReason, "有道（缓存）播放失败")
                }
            }
            return
        }
        val fetched = downloadYoudaoDetailed(word, accent)
        if (fetched.bytes == null || fetched.bytes.isEmpty()) {
            if (generation == playGeneration) {
                speak(generation, word, accent, vivoReason, fetched.reason ?: "有道无返回值")
            }
            return
        }
        val file = cache.put(word, accent, SOURCE_YOUDAO, fetched.bytes)
        if (generation == playGeneration) {
            playFile(generation, file) {
                scope.launch {
                    speak(generation, word, accent, vivoReason, "有道音频播放失败")
                }
            }
        }
    }

    /** 第一级：有道。命中缓存直接播；未命中下载并写缓存；失败落到 vivo。 */
    private suspend fun playYoudaoFirst(generation: Int, word: String, accent: Accent) {
        if (generation != playGeneration) return
        val cached = cache.find(word, accent, SOURCE_YOUDAO)
        if (cached != null) {
            playFile(generation, cached) {
                scope.launch { playVivoFallback(generation, word, accent, "有道（缓存）播放失败") }
            }
            return
        }
        val fetched = downloadYoudaoDetailed(word, accent)
        if (fetched.bytes == null || fetched.bytes.isEmpty()) {
            if (generation == playGeneration) playVivoFallback(generation, word, accent, fetched.reason ?: "有道无返回值")
            return
        }
        val file = cache.put(word, accent, SOURCE_YOUDAO, fetched.bytes)
        if (generation == playGeneration) {
            playFile(generation, file) {
                scope.launch { playVivoFallback(generation, word, accent, "有道音频播放失败") }
            }
        }
    }

    /** 第二级：vivo TTS。命中 vivo 缓存直接播；未命中合成并写缓存；失败落到系统 TTS。 */
    private suspend fun playVivoFallback(
        generation: Int,
        word: String,
        accent: Accent,
        youdaoReason: String,
    ) {
        if (generation != playGeneration) return
        val cached = cache.find(word, accent, SOURCE_VIVO)
        if (cached != null) {
            playFile(generation, cached) {
                scope.launch { speak(generation, word, accent, "vivo 缓存音频播放失败", youdaoReason) }
            }
            return
        }
        val result = vivoClient.synthesize(word, langType = accent.ttsLangType)
        if (generation != playGeneration) return
        val vivoReason: String? = when (result) {
            is VivoTtsResult.Audio -> if (result.bytes.isEmpty()) "vivo 返回空音频" else null
            is VivoTtsResult.Error -> result.message
        }
        val audio = (result as? VivoTtsResult.Audio)?.bytes
        if (audio == null || audio.isEmpty()) {
            if (generation == playGeneration) speak(generation, word, accent, vivoReason, youdaoReason)
            return
        }
        val file = cache.put(word, accent, SOURCE_VIVO, preparePlayable(audio))
        if (generation == playGeneration) {
            playFile(generation, file) {
                scope.launch { speak(generation, word, accent, "vivo 合成音频播放失败", youdaoReason) }
            }
        }
    }

    /** 单飞下载，prefetch 用：只关心是否取到字节。 */
    private suspend fun downloadYoudao(word: String, accent: Accent): ByteArray? =
        downloadYoudaoDetailed(word, accent).bytes

    /** 单飞下载：并发请求同一 <音色>:<文本> 只发一次 HTTP，其余复用结果；带失败原因。 */
    private suspend fun downloadYoudaoDetailed(word: String, accent: Accent): YoudaoFetch {
        val key = "${accent.name}:$word"
        inFlight[key]?.let { return it.await() }
        val deferred = scope.async(Dispatchers.IO) { fetchYoudaoDetailed(word, accent) }
        inFlight[key] = deferred
        try {
            return deferred.await()
        } finally {
            inFlight.remove(key)
        }
    }

    /** 有道下载。只接受 200 且 content-type 为 audio 类型、或缺失 content-type 但可识别音频容器的响应；错误页/空体返回 null 触发回退。 */
    private fun fetchYoudaoDetailed(word: String, accent: Accent): YoudaoFetch = try {
        val conn = URL("https://dict.youdao.com/dictvoice?audio=${word.encodeUrl()}&type=${accent.youdaoType}")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode != 200) return YoudaoFetch(null, "HTTP ${conn.responseCode}")
            val type = conn.contentType
            val audioType = type != null && type.startsWith("audio/", ignoreCase = true)
            if (type != null && type.isNotBlank() && !audioType) return YoudaoFetch(null, "content-type=$type 非音频")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return YoudaoFetch(null, "空响应体")
            if (!audioType && !looksLikeAudio(bytes)) return YoudaoFetch(null, "响应非音频格式")
            YoudaoFetch(bytes, null)
        } finally {
            conn.disconnect()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        YoudaoFetch(null, "网络异常: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun playFile(generation: Int, file: File, onFailure: () -> Unit) {
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
                    onFailure()
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
            onFailure()
        }
    }

    /** vivo 音频可能是有封装（wav/mp3）也可能是无容器 PCM，统一成 MediaPlayer 可播放的字节。 */
    private fun preparePlayable(audio: ByteArray): ByteArray = when {
        looksLikeWav(audio) -> audio
        looksLikeMp3(audio) -> audio
        // vivo `/fy/tts` 的 auf=audio/L16;rate=16000 会返回无容器 PCM,MediaPlayer 无法直接播,
        // 补一个 WAV 头让它可播放;若将来返回其它已封装格式则走上面的检测分支,此处是兜底。
        else -> wavWrap(audio)
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
    private fun speak(
        generation: Int,
        word: String,
        accent: Accent,
        vivoReason: String?,
        youdaoReason: String,
    ) {
        if (generation != playGeneration) return
        PronunciationDiagnostics.record(FallbackDiagnostics(word, accent, vivoReason, youdaoReason))
        // 可通过 adb 拉取：adb logcat -s CDictAudio:I
        Log.w(
            TAG,
            "朗读回退系统TTS text=$word accent=$accent vivo=${vivoReason ?: "-"} youdao=$youdaoReason",
        )
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
        private const val TAG = "CDictAudio"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        private const val SOURCE_VIVO = "vivo"
        private const val SOURCE_YOUDAO = "youdao"
    }
}

enum class Accent(val path: String, val youdaoType: Int, val ttsLangType: String) {
    UK("uk", 1, "en-GBR"),
    US("us", 2, "en-USA"),
}

/** 有道静态音频拉取结果；[bytes] 为空表示失败，[reason] 说明失败原因以便诊断。 */
private data class YoudaoFetch(val bytes: ByteArray?, val reason: String?)
