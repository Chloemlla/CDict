package com.chloemlla.cdict.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE hashKey = :key")
    suspend fun getByKey(key: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranslationCacheEntity)

    @Query("UPDATE translation_cache SET lastAccessedAt = :now WHERE hashKey = :key")
    suspend fun touch(key: String, now: Long)

    @Query("UPDATE translation_cache SET isFavorite = :isFavorite WHERE hashKey = :key")
    suspend fun setFavorite(key: String, isFavorite: Boolean)

    @Query("SELECT isFavorite FROM translation_cache WHERE hashKey = :key")
    suspend fun favoriteFlag(key: String): Int?

    @Query("SELECT COUNT(*) FROM translation_cache WHERE isFavorite = 0")
    suspend fun nonFavoriteCount(): Int

    @Query(
        "DELETE FROM translation_cache WHERE hashKey IN (" +
            "SELECT hashKey FROM translation_cache WHERE isFavorite = 0 " +
            "ORDER BY lastAccessedAt ASC LIMIT :limit)",
    )
    suspend fun evictNonFavorite(limit: Int)
}