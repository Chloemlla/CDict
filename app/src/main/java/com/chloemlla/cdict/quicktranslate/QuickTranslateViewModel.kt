package com.chloemlla.cdict.quicktranslate

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.data.QuickWord
import com.chloemlla.cdict.core.data.QuickWordLookup
import com.chloemlla.cdict.core.translate.RoomTranslationCache
import com.chloemlla.cdict.core.translate.TranslationCache
import com.chloemlla.cdict.core.translate.TranslationCacheKey
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationLimits
import com.chloemlla.cdict.core.translate.TranslationOutcome
import com.chloemlla.cdict.core.translate.TranslationRequest
import com.chloemlla.cdict.core.translate.VivoTranslationClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 选中文字快速翻译弹窗的一次性状态：原文、译文、词库命中与失败原因。 */
data class QuickTranslateState(
    val source: String = "",
    val direction: TranslationDirection = TranslationDirection.AUTO_TO_ZH,
    val translating: Boolean = false,
    val translation: String? = null,
    val phonetic: String? = null,
    val error: String? = null,
    val entry: QuickWord? = null,
)

/**
 * 驱动系统文本选择工具条唤起的快速翻译弹窗。
 *
 * 两条支线并行：词库精确命中（离线、毫秒级，命中后弹窗才显示「前往」）与在线翻译
 * （命中三层缓存时零延迟）。二者互不阻塞，任一失败都不影响另一条的展示。
 */
class QuickTranslateViewModel(
    private val appContext: Context,
    private val client: VivoTranslationClient = VivoTranslationClient(),
    private val cache: TranslationCache = TranslationCache.NoOp,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickTranslateState())
    val state: StateFlow<QuickTranslateState> = _state.asStateFlow()

    // 弹窗只处理一次传入的选区；配置变更后 ViewModel 存活，不得重复发起请求。
    private var started = false
    private var translateJob: Job? = null

    // viewModelScope 跑在 Dispatchers.Main.immediate 上：内存缓存命中时协程体在 translateJob 被赋值
    // 之前就同步跑完了，拿 job 判等永远为 false。世代号在 launch 之前自增，才是可靠的“我还是最新请求吗”。
    private var translateGeneration = 0

    fun start(rawText: String) {
        if (started) return
        started = true
        val source = normalizeSource(rawText)
        if (source.isEmpty()) return
        _state.value = QuickTranslateState(source = source, direction = defaultDirection(source))
        lookupEntry(source)
        translate()
    }

    /** 在「译为中文」与「译为英文」之间切换，并立即重译。 */
    fun toggleDirection() {
        val current = _state.value
        val next =
            if (current.direction == TranslationDirection.AUTO_TO_ZH) TranslationDirection.AUTO_TO_EN
            else TranslationDirection.AUTO_TO_ZH
        _state.update { it.copy(direction = next, translation = null, phonetic = null, error = null) }
        translate()
    }

    fun retry() = translate()

    private fun lookupEntry(source: String) {
        viewModelScope.launch {
            val hit = QuickWordLookup.find(appContext, source)
            if (hit != null) _state.update { it.copy(entry = hit) }
        }
    }

    private fun translate() {
        val current = _state.value
        val text = current.source
        if (text.isEmpty()) return
        val direction = current.direction
        translateJob?.cancel()
        // 首帧就是「正在翻译…」：留到协程里再置会先闪一下「暂无译文」。
        _state.update { it.copy(translating = true, error = null) }
        val gen = ++translateGeneration
        translateJob = viewModelScope.launch {
            val key = TranslationCacheKey.of(text, direction)
            // 缓存只是可选加速：磁盘写满时 Room 会抛 SQLiteFullException，不该把翻译功能一起拖崩。
            val cached = try {
                cache.get(key)
            } catch (e: SQLiteException) {
                null
            }
            if (cached != null) {
                if (gen == translateGeneration) {
                    _state.update {
                        it.copy(
                            translating = false,
                            translation = cached.translations.firstOrNull(),
                            phonetic = cached.phonetic,
                            error = null,
                        )
                    }
                }
                return@launch
            }
            when (val outcome = client.translate(TranslationRequest(listOf(text), direction))) {
                is TranslationOutcome.Success -> {
                    try {
                        cache.put(key, text, direction, outcome.result)
                    } catch (e: SQLiteException) {
                        // 写缓存失败不影响本次译文展示。
                    }
                    if (gen == translateGeneration) {
                        _state.update {
                            it.copy(
                                translating = false,
                                translation = outcome.result.translations.firstOrNull(),
                                phonetic = outcome.result.phonetic,
                                error = null,
                            )
                        }
                    }
                }
                is TranslationOutcome.Failure ->
                    if (gen == translateGeneration) {
                        _state.update { it.copy(translating = false, error = outcome.message) }
                    }
            }
        }
    }

    companion object {
        /** 与翻译页共用的原文上限：超长选区截断后再送翻译，避免网关直接拒绝。 */
        const val MAX_SOURCE_LENGTH = TranslationLimits.MAX_SOURCE_LENGTH

        fun normalizeSource(raw: String): String = raw.trim().take(MAX_SOURCE_LENGTH)

        /** 选区含中日韩汉字时默认译为英文，其余一律译为中文。 */
        fun defaultDirection(source: String): TranslationDirection =
            if (source.any { it.code in 0x4E00..0x9FFF }) TranslationDirection.AUTO_TO_EN
            else TranslationDirection.AUTO_TO_ZH
    }
}

class QuickTranslateViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        return QuickTranslateViewModel(
            appContext,
            VivoTranslationClient(),
            RoomTranslationCache.shared(appContext),
        ) as T
    }
}
