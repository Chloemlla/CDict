package com.chloemlla.cdict.core.translate

/**
 * 进程内最近最少使用（LRU）映射，做第 1 层内存缓存。
 *
 * 基于 accessOrder=true 的 LinkedHashMap：读/写都会把 key 移到队尾，队首即最久未用；
 * size 超过 [maxSize] 时由 removeEldestEntry 自动剔除队首。方法加锁保证跨线程安全
 * （UI 协程与 Room IO 协程会交错访问）。
 */
class MemoryLruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxSize
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? = map.put(key, value)

    @Synchronized
    fun size(): Int = map.size
}