package com.chloemlla.cdict.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE hash_key = :key")
    suspend fun getByKey(key: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranslationCacheEntity)

    @Query("UPDATE translation_cache SET last_accessed_at = :now WHERE hash_key = :key")
    suspend fun touch(key: String, now: Long)

    @Query("UPDATE translation_cache SET is_favorite = :isFavorite WHERE hash_key = :key")
    suspend fun setFavorite(key: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM translation_cache WHERE is_favorite = 0")
    suspend fun nonFavoriteCount(): Int

    @Query(
        "DELETE FROM translation_cache WHERE hash_key IN (" +
            "SELECT hash_key FROM translation_cache WHERE is_favorite = 0 " +
            "ORDER BY last_accessed_at ASC LIMIT :limit)",
    )
    suspend fun evictNonFavorite(limit: Int)
}