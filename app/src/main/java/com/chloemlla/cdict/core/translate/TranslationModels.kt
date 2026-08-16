package com.chloemlla.cdict.core.translate

enum class TranslationDirection(val from: String, val to: String, val label: String) {
    AUTO_TO_ZH("auto", "zh-CHS", "自动→中文"),
    AUTO_TO_EN("auto", "en", "自动→英文"),
    ZH_TO_EN("zh-CHS", "en", "中文→英文"),
    EN_TO_ZH("en", "zh-CHS", "英文→中文"),
    AUTO_TO_JA("auto", "ja", "自动→日"),
    AUTO_TO_KO("auto", "ko", "自动→韩"),
    AUTO_TO_FR("auto", "fr", "自动→法"),
    AUTO_TO_ES("auto", "es", "自动→西"),
    AUTO_TO_RU("auto", "ru", "自动→俄"),
    JA_TO_ZH("ja", "zh-CHS", "日→中"),
    ZH_TO_JA("zh-CHS", "ja", "中→日"),
    KO_TO_ZH("ko", "zh-CHS", "韩→中"),
    ZH_TO_KO("zh-CHS", "ko", "中→韩"),
    FR_TO_ZH("fr", "zh-CHS", "法→中"),
    ZH_TO_FR("zh-CHS", "fr", "中→法"),
    RU_TO_ZH("ru", "zh-CHS", "俄→中"),
    ZH_TO_RU("zh-CHS", "ru", "中→俄"),
    ES_TO_ZH("es", "zh-CHS", "西→中"),
    ZH_TO_ES("zh-CHS", "es", "中→西"),
    DE_TO_ZH("de", "zh-CHS", "德→中"),
    ZH_TO_DE("zh-CHS", "de", "中→德"),
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

sealed interface LanguageListOutcome {
    data class Success(val languages: Set<String>) : LanguageListOutcome
    data class Failure(val message: String) : LanguageListOutcome
}
