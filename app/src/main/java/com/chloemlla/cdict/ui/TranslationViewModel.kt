package com.chloemlla.cdict.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.translate.LanguageListOutcome
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
        viewModelScope.launch {
            _state.value = TranslationUiState.Translating
            val outcome = client.translate(TranslationRequest(listOf(text), _direction.value))
            _state.value = when (outcome) {
                is TranslationOutcome.Success -> TranslationUiState.Success(outcome.result)
                is TranslationOutcome.Failure -> TranslationUiState.Failure(outcome.message)
            }
        }
    }
}

class TranslationViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TranslationViewModel(VivoTranslationClient()) as T
    }
}
