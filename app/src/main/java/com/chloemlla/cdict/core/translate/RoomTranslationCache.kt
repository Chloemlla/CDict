package com.chloemlla.cdict.core.translate

import android.content.Context
import com.chloemlla.cdict.core.data.TranslationCacheDao
import com.chloemlla.cdict.core.data.TranslationCacheDatabase
import com.chloemlla.cdict.core.data.TranslationCacheEntity
import org.json.JSONArray

/**
 * 三层缓存中的“内存 + 磁盘”两层编排实现，远端仍由 VivoTranslationClient 出网。
 *
 * 命中路径：内存 LRU →（未中）SQLite 查询 → 命中则 touch last_accessed_at 并提升进内存 →（仍未中）回 null。
 * 写路径：记忆命中即回填内存 + 磁盘；非收藏总量超 maxDiskEntries 时按 last_accessed_at 升序一次淘汰 evictBatch 条，
 * 收藏行（is_favorite=1）永不参与淘汰。
 */
class RoomTranslationCache(
    private val dao: TranslationCacheDao,
    private val memory: MemoryLruCache<String, TranslationResult> = MemoryLruCache(DEFAULT_MEMORY_ENTRIES),
    private val maxDiskEntries: Int = DEFAULT_DISK_ENTRIES,
    private val evictBatch: Int = DEFAULT_EVICT_BATCH,
) : TranslationCache {

    override suspend fun get(key: String): TranslationResult? {
        memory.get(key)?.let { return it }
        val row = dao.getByKey(key) ?: return null
        dao.touch(key, System.currentTimeMillis())
        val result = row.toResult()
        memory.put(key, result)
        return result
    }

    override suspend fun put(
        key: String,
        sourceText: String,
        direction: TranslationDirection,
        result: TranslationResult,
    ) {
        memory.put(key, result)
        val now = System.currentTimeMillis()
        // upsert 用 REPLACE（DELETE+INSERT）实现，重译同一条会丢掉收藏标记与首次写入时间，须先读回来。
        val existing = dao.getByKey(key)
        dao.upsert(
            TranslationCacheEntity(
                hashKey = key,
                sourceText = sourceText,
                direction = "${direction.from}>${direction.to}",
                from = result.from,
                to = result.to,
                translationsJson = JSONArray(result.translations).toString(),
                phonetic = result.phonetic,
                isFavorite = existing?.isFavorite ?: 0,
                createdAt = existing?.createdAt ?: now,
                lastAccessedAt = now,
            ),
        )
        if (dao.nonFavoriteCount() > maxDiskEntries) {
            dao.evictNonFavorite(evictBatch)
        }
    }

    override suspend fun markFavorite(key: String, favorite: Boolean) {
        dao.setFavorite(key, favorite)
    }

    override suspend fun isFavorite(key: String): Boolean = dao.favoriteFlag(key) == 1

    private fun TranslationCacheEntity.toResult(): TranslationResult {
        val translations = runCatching {
            val arr = JSONArray(translationsJson)
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }.getOrNull() ?: emptyList()
        // 归一化前写入的行仍存着 phonetic 原始 JSON（内含 TTS URL 与凭据字段），读出时再过一遍。
        return TranslationResult(translations, from, to, phonetic?.let(::normalizePhonetic))
    }

    companion object {
        const val DEFAULT_MEMORY_ENTRIES = 30
        const val DEFAULT_DISK_ENTRIES = 500
        const val DEFAULT_EVICT_BATCH = 50

        @Volatile
        private var instance: RoomTranslationCache? = null

        /** 进程级单例：多个入口（翻译页、快译弹窗、词条详情）共用同一个连接池与内存 LRU。 */
        fun shared(context: Context): RoomTranslationCache =
            instance ?: synchronized(this) {
                instance ?: RoomTranslationCache(
                    TranslationCacheDatabase.open(context.applicationContext).translationCacheDao(),
                ).also { instance = it }
            }
    }
}