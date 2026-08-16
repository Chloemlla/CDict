package com.chloemlla.cdict.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.data.TranslationCacheDatabase
import com.chloemlla.cdict.core.translate.LanguageListOutcome
import com.chloemlla.cdict.core.translate.RoomTranslationCache
import com.chloemlla.cdict.core.translate.TranslationCache
import com.chloemlla.cdict.core.translate.TranslationCacheKey
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationOutcome
import com.chloemlla.cdict.core.translate.TranslationRequest
import com.chloemlla.cdict.core.translate.TranslationResult
import com.chloemlla.cdict.core.translate.VivoTranslationClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onDirectionChange(direction: TranslationDirection) {
        _direction.value = direction
    }

    /** 仅在显式调用时才发起网络请求，构造 VM 本身不会触发网络。 */
    fun loadSupportedLanguages() {
        viewModelScope.launch {
            val outcome = client.fetchLanguages()
            if (outcome is LanguageListOutcome.Success) {
                _supportedLanguages.value = outcome.languages.sorted()
            }
        }
    }

    fun translate() {
        val text = _query.value.trim()
        if (text.isEmpty()) return
        val direction = _direction.value
        viewModelScope.launch {
            val key = TranslationCacheKey.of(text, direction)
            cache.get(key)?.let { cached ->
                // 内存/磁盘命中：跳过网页请求，0ms 瞬间渲染。
                _state.value = TranslationUiState.Success(cached)
                return@launch
            }
            _state.value = TranslationUiState.Translating
            when (val outcome = client.translate(TranslationRequest(listOf(text), direction))) {
                is TranslationOutcome.Success -> {
                    cache.put(key, text, direction, outcome.result)
                    _state.value = TranslationUiState.Success(outcome.result)
                }
                is TranslationOutcome.Failure -> _state.value = TranslationUiState.Failure(outcome.message)
            }
        }
    }
}

class TranslationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = TranslationCacheDatabase.open(context.applicationContext)
        val cache = RoomTranslationCache(db.translationCacheDao())
        @Suppress("UNCHECKED_CAST")
        return TranslationViewModel(VivoTranslationClient(), cache) as T
    }
}
