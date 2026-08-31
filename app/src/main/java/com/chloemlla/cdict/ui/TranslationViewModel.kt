package com.chloemlla.cdict.ui

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.translate.LanguageListOutcome
import com.chloemlla.cdict.core.translate.RoomTranslationCache
import com.chloemlla.cdict.core.translate.TranslationCache
import com.chloemlla.cdict.core.translate.TranslationCacheKey
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationLimits
import com.chloemlla.cdict.core.translate.TranslationOutcome
import com.chloemlla.cdict.core.translate.TranslationRequest
import com.chloemlla.cdict.core.translate.TranslationResult
import com.chloemlla.cdict.core.translate.VivoTranslationClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Translating : TranslationUiState
    data class Success(val result: TranslationResult) : TranslationUiState
    data class Failure(val message: String) : TranslationUiState
}

class TranslationViewModel(
    private val client: VivoTranslationClient = VivoTranslationClient(),
    private val cache: TranslationCache = TranslationCache.NoOp,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _direction = MutableStateFlow(TranslationDirection.AUTO_TO_ZH)
    val direction: StateFlow<TranslationDirection> = _direction.asStateFlow()

    private val _state = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    val state: StateFlow<TranslationUiState> = _state.asStateFlow()

    private val _supportedLanguages = MutableStateFlow<List<String>>(emptyList())
    val supportedLanguages: StateFlow<List<String>> = _supportedLanguages.asStateFlow()

    /** 当前译文是否已收藏（收藏行免疫缓存 LRU 淘汰，离线也还能查到）。 */
    private val _favorite = MutableStateFlow(false)
    val favorite: StateFlow<Boolean> = _favorite.asStateFlow()

    // The in-flight translate job, so a new translate (or an input/direction change) cancels
    // the previous one and a late response can never overwrite a newer request's result.
    private var translateJob: Job? = null

    // viewModelScope 跑在 Dispatchers.Main.immediate 上：内存缓存命中时协程体在 translateJob 被赋值
    // 之前就同步跑完了，拿 job 判等永远为 false。世代号在 launch 之前自增，才是可靠的“我还是最新请求吗”。
    private var translateGeneration = 0

    private var languagesJob: Job? = null

    /** 收藏针对的缓存键，成功出译文后才有值。 */
    private var favoriteKey: String? = null

    fun onQueryChange(text: String) {
        _query.value = text
        translateJob?.cancel()
        _state.value = TranslationUiState.Idle
        clearFavorite()
    }

    fun onDirectionChange(direction: TranslationDirection) {
        _direction.value = direction
        translateJob?.cancel()
        _state.value = TranslationUiState.Idle
        clearFavorite()
    }

    private fun clearFavorite() {
        favoriteKey = null
        _favorite.value = false
    }

    /** 收藏/取消收藏当前译文；缓存不可用时静默失败，不影响已显示的译文。 */
    fun toggleFavorite() {
        val key = favoriteKey ?: return
        val next = !_favorite.value
        _favorite.value = next
        viewModelScope.launch {
            try {
                cache.markFavorite(key, next)
            } catch (e: SQLiteException) {
                _favorite.value = !next
            }
        }
    }

    /** 仅在显式调用时才发起网络请求，构造 VM 本身不会触发网络。 */
    fun loadSupportedLanguages() {
        if (_supportedLanguages.value.isNotEmpty() || languagesJob?.isActive == true) return
        languagesJob = viewModelScope.launch {
            when (val outcome = client.fetchLanguages()) {
                is LanguageListOutcome.Success ->
                    _supportedLanguages.value = outcome.languages.sorted()
                is LanguageListOutcome.Failure ->
                    Log.w(TAG, "语言列表加载失败：${outcome.message}")
            }
        }
    }

    fun translate() {
        val text = _query.value.trim().take(TranslationLimits.MAX_SOURCE_LENGTH)
        if (text.isEmpty()) return
        val direction = _direction.value
        translateJob?.cancel()
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
                // 内存/磁盘命中：跳过网络请求，0ms 瞬间渲染。
                if (gen == translateGeneration) {
                    _state.value = TranslationUiState.Success(cached)
                    adoptFavoriteKey(key)
                }
                return@launch
            }
            _state.value = TranslationUiState.Translating
            when (val outcome = client.translate(TranslationRequest(listOf(text), direction))) {
                is TranslationOutcome.Success -> {
                    try {
                        cache.put(key, text, direction, outcome.result)
                    } catch (e: SQLiteException) {
                        Log.w(TAG, "翻译缓存写入失败：${e.javaClass.simpleName}")
                    }
                    // 旧请求（被取代后仍在跑）不得覆盖新请求的结果。
                    if (gen == translateGeneration) {
                        _state.value = TranslationUiState.Success(outcome.result)
                        adoptFavoriteKey(key)
                    }
                }
                is TranslationOutcome.Failure ->
                    if (gen == translateGeneration) {
                        _state.value = TranslationUiState.Failure(outcome.message)
                    }
            }
        }
    }

    private suspend fun adoptFavoriteKey(key: String) {
        favoriteKey = key
        _favorite.value = try {
            cache.isFavorite(key)
        } catch (e: SQLiteException) {
            false
        }
    }

    private companion object {
        const val TAG = "CDictTranslate"
    }
}

class TranslationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TranslationViewModel(
            VivoTranslationClient(),
            RoomTranslationCache.shared(context),
        ) as T
    }
}
