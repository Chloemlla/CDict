package com.chloemlla.cdict.core.translate

import com.chloemlla.cdict.core.data.TranslationCacheDao
import com.chloemlla.cdict.core.data.TranslationCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTranslationCacheTest {

    private val dao = FakeDao()
    private fun cache(maxDisk: Int = 3) = RoomTranslationCache(
        dao = dao,
        maxDiskEntries = maxDisk,
        evictBatch = 3,
    )

    @Test
    fun `miss returns null`() = runTest {
        dao.clear()
        val key = TranslationCacheKey.of("hello", TranslationDirection.AUTO_TO_ZH)
        assertNull(cache().get(key))
    }

    @Test
    fun `key folds surrounding blanks but keeps case`() {
        val dir = TranslationDirection.EN_TO_ZH
        // US（美国）与 us（我们）、May 与 may 是不同的词，不能共用一条译文。
        assertNotEquals(TranslationCacheKey.of("US", dir), TranslationCacheKey.of("us", dir))
        assertEquals(TranslationCacheKey.of(" us\n", dir), TranslationCacheKey.of("us", dir))
    }

    @Test
    fun `put then get round-trips full result from disk`() = runTest {
        dao.clear()
        val dir = TranslationDirection.AUTO_TO_ZH
        val key = TranslationCacheKey.of("hello", dir)
        val result = TranslationResult(listOf("你好", "您好"), "en", "zh-CHS", "həˈloʊ")
        cache().put(key, "hello", dir, result)
        // 新实例（同上 dao）模拟进程重启：仅走磁盘，验证序列化往返。
        val cold = cache()
        assertEquals(result, cold.get(key))
    }

    @Test
    fun `eviction drops oldest non-favorite and keeps favorite`() = runTest {
        dao.clear()
        val dir = TranslationDirection.AUTO_TO_ZH
        val keyA = TranslationCacheKey.of("A", dir)
        val keyB = TranslationCacheKey.of("B", dir)
        val keyC = TranslationCacheKey.of("C", dir)
        val keyD = TranslationCacheKey.of("D", dir)
        val keyE = TranslationCacheKey.of("E", dir)

        fun resultOf(t: String) = TranslationResult(listOf(t), "en", "zh-CHS", null)
        cache().apply {
            put(keyA, "A", dir, resultOf("a1"))
            put(keyB, "B", dir, resultOf("b1"))
            put(keyC, "C", dir, resultOf("c1"))
        }
        // 强制不同的访问时间，避免 System.currentTimeMillis() 落在同一毫秒产生歧义。
        dao.forceLastAccessed(keyA, 100L)
        dao.forceLastAccessed(keyB, 200L)
        dao.forceLastAccessed(keyC, 300L)
        cache().markFavorite(keyB, true)

        cache().put(keyD, "D", dir, resultOf("d1")) // 非收藏 A,C + 新 D = 3，未超上限
        cache().put(keyE, "E", dir, resultOf("e1")) // 超上限 → 淘汰最旧的 3 条非收藏（A,C,D）
        assertTrue(keyB in dao.keys())
        assertTrue(keyE in dao.keys())
        assertFalse(keyA in dao.keys())
        assertFalse(keyC in dao.keys())
        assertFalse(keyD in dao.keys())
    }

    @Test
    fun `get from disk touches access time and promotes to memory`() = runTest {
        dao.clear()
        val dir = TranslationDirection.AUTO_TO_ZH
        val key = TranslationCacheKey.of("promote", dir)
        val result = TranslationResult(listOf("提升"), "en", "zh-CHS", null)
        cache().put(key, "promote", dir, result) // 落库（该缓存实例的内存随即丢弃）
        dao.forceLastAccessed(key, 1L) // 人为把访问时间弄成最旧
        val cold = cache() // 内存为空 → 走磁盘路径
        assertEquals(result, cold.get(key))
        assertTrue(dao.row(key)!!.lastAccessedAt > 1L) // touch 已刷新访问时间
        dao.clear() // 清空磁盘
        assertEquals(result, cold.get(key)) // 仅靠已提升进内存的副本命中
    }

    @Test
    fun `legacy row with raw phonetic json is sanitized on read`() = runTest {
        dao.clear()
        val dir = TranslationDirection.AUTO_TO_EN
        val key = TranslationCacheKey.of("取决于", dir)
        val leak =
            """[{"filename":"https://openapi.example.com/vivo/ttsapi?q=depending&appKey=TEST_APP_KEY","ttsId":"x-phonetic-0","text":"dɪˈpendɪŋ","type":"auto"}]"""
        dao.upsert(
            TranslationCacheEntity(
                hashKey = key,
                sourceText = "取决于",
                direction = "${dir.from}>${dir.to}",
                from = "zh-CHS",
                to = "en",
                translationsJson = """["depending"]""",
                phonetic = leak,
                isFavorite = 0,
                createdAt = 1L,
                lastAccessedAt = 1L,
            ),
        )
        assertEquals("dɪˈpendɪŋ", cache().get(key)!!.phonetic)
    }

    /** 内存 DAO：替换实现细节，聚焦编排逻辑（LRU 排序、淘汰、收藏免疫）。 */
    private class FakeDao : TranslationCacheDao {
        private val rows = linkedMapOf<String, TranslationCacheEntity>()

        fun clear() = rows.clear()
        fun keys() = rows.keys.toSet()
        fun row(key: String) = rows[key]
        fun forceLastAccessed(key: String, now: Long) {
            rows[key]?.let { rows[key] = it.copy(lastAccessedAt = now) }
        }

        override suspend fun getByKey(key: String): TranslationCacheEntity? = rows[key]
        override suspend fun upsert(entity: TranslationCacheEntity) {
            rows[entity.hashKey] = entity
        }

        override suspend fun touch(key: String, now: Long) {
            rows[key]?.let { rows[key] = it.copy(lastAccessedAt = now) }
        }

        override suspend fun setFavorite(key: String, isFavorite: Boolean) {
            rows[key]?.let { rows[key] = it.copy(isFavorite = if (isFavorite) 1 else 0) }
        }

        override suspend fun favoriteFlag(key: String): Int? = rows[key]?.isFavorite

        override suspend fun nonFavoriteCount(): Int = rows.values.count { it.isFavorite == 0 }

        override suspend fun evictNonFavorite(limit: Int) {
            rows.values
                .filter { it.isFavorite == 0 }
                .sortedBy { it.lastAccessedAt }
                .take(limit)
                .forEach { rows.remove(it.hashKey) }
        }
    }
}