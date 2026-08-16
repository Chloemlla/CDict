package com.chloemlla.cdict.core.translate

import java.security.MessageDigest

/**
 * 三层缓存的统一门面（第 1 层内存 + 第 2 层 SQLite）。第 3 层远端网关由 VivoTranslationClient 负责，
 * 本接口只描述“命中与回填”，不入网。
 */
interface TranslationCache {
    /** 先查内存、再查磁盘；磁盘命中会刷新 last_accessed_at 并提升到内存。未命中返回 null。 */
    suspend fun get(key: String): TranslationResult?

    /** 回填：写入内存，并异步写盘；非收藏记录总量超上限时触发 LRU 淘汰。 */
    suspend fun put(key: String, sourceText: String, direction: TranslationDirection, result: TranslationResult)

    /** 置为收藏后免疫 LRU 淘汰（用户资产，需手动移除才会被清）。 */
    suspend fun markFavorite(key: String, favorite: Boolean)

    object NoOp : TranslationCache {
        override suspend fun get(key: String): TranslationResult? = null
        override suspend fun put(
            key: String,
            sourceText: String,
            direction: TranslationDirection,
            result: TranslationResult,
        ) = Unit

        override suspend fun markFavorite(key: String, favorite: Boolean) = Unit
    }
}

/**
 * 缓存键：对归一化（trim + 小写）后的原文与翻译方向做 SHA-256，得十六进制指纹。
 * 把 from/to 纳入指纹，同一句原文在不同方向下是不同键，避免 A→中文 与 A→日文 互相污染。
 * 归一化把首尾空白与大小写差异折叠成同一键，规避 AI 翻译对格式敏感导致的重复请求。
 */
object TranslationCacheKey {
    fun of(text: String, direction: TranslationDirection): String {
        val normalized = text.trim().lowercase()
        // 用 NUL 作分段符（原文几乎不可能含 NUL），再用 0.toChar() 在运行时构造，源码不含控制字符。
        val sep = 0.toChar()
        val raw = "$normalized$sep${direction.from}$sep${direction.to}"
        return sha256Hex(raw)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}