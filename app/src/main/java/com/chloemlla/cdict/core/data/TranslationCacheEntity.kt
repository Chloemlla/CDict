package com.chloemlla.cdict.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单条 vivo 网关翻译缓存行，落在独立的 translation_cache.db（不触碰随包分发的只读 dict.db）。
 *
 * 索引策略：LRU 淘汰只对非收藏记录生效，故 (is_favorite, last_accessed_at) 复合索引正好
 * 覆盖 ORDER BY last_accessed_at ASC 的删除路径；收藏记录因 is_favorite=1 排在后面不会误伤。
 */
@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["is_favorite", "last_accessed_at"], name = "idx_translation_cache_lru"),
    ],
)
data class TranslationCacheEntity(
    @PrimaryKey val hashKey: String,
    val sourceText: String,
    val direction: String,
    val from: String,
    val to: String,
    /** translation 列表序列化为 JSON 数组文本，保留多译文结构。 */
    val translationsJson: String,
    val phonetic: String?,
    @ColumnInfo(defaultValue = "0") val isFavorite: Int,
    val createdAt: Long,
    val lastAccessedAt: Long,
)