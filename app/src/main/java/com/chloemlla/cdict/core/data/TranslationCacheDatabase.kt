package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 翻译缓存的独立数据库。刻意与只读词库 dict.db 分库：
 * 加表/改版本不会触发破坏性迁移而清空随包预置的词库，也用不到 fallbackToDestructiveMigration。
 */
@Database(
    entities = [TranslationCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TranslationCacheDatabase : RoomDatabase() {
    abstract fun translationCacheDao(): TranslationCacheDao

    companion object {
        @Volatile
        private var instance: TranslationCacheDatabase? = null

        fun open(context: Context): TranslationCacheDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranslationCacheDatabase::class.java,
                    "translation_cache.db",
                ).build().also { instance = it }
            }
    }
}