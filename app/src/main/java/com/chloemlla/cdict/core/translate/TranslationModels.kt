package com.chloemlla.cdict.core.translate

enum class TranslationDirection(val from: String, val to: String, val label: String) {
    AUTO_TO_ZH("auto", "zh-CHS", "自动→中文"),
    AUTO_TO_EN("auto", "en", "自动→英文"),
    ZH_TO_EN("zh-CHS", "en", "中文→英文"),
    EN_TO_ZH("en", "zh-CHS", "英文→中文"),
}

data class TranslationRequest(
    val texts: List<String>,
    val direction: TranslationDirection,
)

data class TranslationResult(
    val translations: List<String>,
    val from: String,
    val to: String,
    val phonetic: String?,
)

sealed interface TranslationOutcome {
    data class Success(val result: TranslationResult) : TranslationOutcome
    data class Failure(val message: String) : TranslationOutcome
}
