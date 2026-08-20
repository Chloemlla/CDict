package com.chloemlla.cdict.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.audio.PronunciationPlayer
import com.chloemlla.cdict.core.audio.PronunciationSpeaker
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationOutcome
import com.chloemlla.cdict.core.translate.TranslationRequest
import com.chloemlla.cdict.core.translate.VivoTranslationClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单个英文片段(释义 / 句子 / 词根 / 派生词 / 助记词)的中文翻译加载状态。 */
sealed interface PhraseUiState {
    data object Loading : PhraseUiState
    data class Done(val zh: String) : PhraseUiState
    data class Error(val message: String) : PhraseUiState
}

/**
 * 词详情页英文内容的“朗读 + 中文翻译”调度器：按英文片段缓存翻译状态(加载中/完成/失败)，
 * 复用在线翻译做英文→中文，用 [PronunciationSpeaker] 播放朗读。构造 VM 不触发任何网络。
 */
class PhraseSpeechViewModel(
    private val client: VivoTranslationClient = VivoTranslationClient(),
    private val speaker: PronunciationSpeaker,
) : ViewModel() {
    private val _states = MutableStateFlow<Map<String, PhraseUiState>>(emptyMap())
    val states: StateFlow<Map<String, PhraseUiState>> = _states.asStateFlow()

    /** 当前正在朗读的英文文本；播放结束自动清空，同文本再次点击切换为停止。 */
    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey: StateFlow<String?> = _speakingKey.asStateFlow()

    /** 为某段英文请求中文译文；已加载/加载中则幂等跳过。 */
    fun translate(en: String) {
        if (en.isBlank()) return
        val currentState = _states.value[en]
        if (currentState is PhraseUiState.Loading || currentState is PhraseUiState.Done) return
        _states.update { it + (en to PhraseUiState.Loading) }
        viewModelScope.launch {
            val outcome = client.translate(
                TranslationRequest(listOf(en), TranslationDirection.EN_TO_ZH),
            )
            val next = when (outcome) {
                is TranslationOutcome.Success -> {
                    val zh = outcome.result.translations.firstOrNull()?.takeIf { it.isNotBlank() }
                    if (zh != null) PhraseUiState.Done(zh) else PhraseUiState.Error("暂无译文")
                }
                is TranslationOutcome.Failure -> PhraseUiState.Error(outcome.message)
            }
            _states.update { current ->
                if (current[en] is PhraseUiState.Loading) current + (en to next) else current
            }
        }
    }

    fun speak(en: String) {
        if (en.isBlank()) return
        // Toggle: clicking the text that is currently speaking stops it.
        if (_speakingKey.value == en) {
            speaker.stop()
            _speakingKey.value = null
            return
        }
        // Auto-clear the speaking state when this audio finishes naturally.
        speaker.onCompletion = {
            if (_speakingKey.value != null) _speakingKey.value = null
        }
        speaker.speak(en)
        _speakingKey.value = en
    }

    override fun onCleared() {
        speaker.release()
        super.onCleared()
    }
}

class PhraseSpeechViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PhraseSpeechViewModel(
            VivoTranslationClient(),
            PronunciationPlayer(context.applicationContext),
        ) as T
    }
}