package com.chloemlla.cdict.core.net

/**
 * CDict 自有后端：客户端所有在线翻译与语音合成请求的唯一出口。
 */
object CDictBackend {
    const val BASE_URL = "https://tts.chloemlla.com"
    const val TRANSLATE_PATH = "/api/cdict/translate"
    const val LANGUAGES_PATH = "/api/cdict/languages"
    const val TTS_PATH = "/api/cdict/tts"

    /** 赞赏渠道列表（图片字节走 [DONATE_PATH] + "/{id}"）。 */
    const val DONATE_PATH = "/api/cdict/donate"

    /** 署名申请提交（拼在 [DONATE_PATH] 之后）。 */
    const val DONATE_CLAIM_SUFFIX = "/claim"

    /** 在线合成引擎（服务端代签名）。 */
    const val SOURCE_ENGINE = "engine"

    /** 词典静态音频（服务端代取）。 */
    const val SOURCE_YOUDAO = "youdao"
}
