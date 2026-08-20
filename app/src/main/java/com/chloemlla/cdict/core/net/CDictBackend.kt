package com.chloemlla.cdict.core.net

/**
 * CDict 自有后端：客户端所有在线翻译与语音合成请求的唯一出口。
 */
object CDictBackend {
    const val BASE_URL = "https://tts.chloemlla.com"
    const val TRANSLATE_PATH = "/api/cdict/translate"
    const val LANGUAGES_PATH = "/api/cdict/languages"
    const val TTS_PATH = "/api/cdict/tts"

    /** 在线合成引擎（服务端代签名）。 */
    const val SOURCE_ENGINE = "engine"

    /** 有道静态音频（服务端代取）。 */
    const val SOURCE_YOUDAO = "youdao"
}
