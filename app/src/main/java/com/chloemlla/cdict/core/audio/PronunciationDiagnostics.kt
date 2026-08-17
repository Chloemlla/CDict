package com.chloemlla.cdict.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次朗读最终落到系统 TTS 时的各级失败原因，供应用内「朗读诊断」排查根因。 */
data class FallbackDiagnostics(
    val text: String,
    val accent: Accent,
    val vivoReason: String?,
    val youdaoReason: String,
)

/**
 * 应用级朗读诊断汇。任何 [PronunciationPlayer] 实例在回退到系统 TTS 时都会写入，
 * About 页独立读取展示，避免依赖某一个玩家实例的生命周期。只保留最近一次。
 */
object PronunciationDiagnostics {
    private val _lastFallback = MutableStateFlow<FallbackDiagnostics?>(null)
    val lastFallback: StateFlow<FallbackDiagnostics?> = _lastFallback.asStateFlow()

    fun record(diag: FallbackDiagnostics) {
        _lastFallback.value = diag
    }

    fun clear() {
        _lastFallback.value = null
    }
}