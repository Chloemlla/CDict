package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DatabaseState {
    data object Loading : DatabaseState
    data class Ready(val database: DictionaryDatabase) : DatabaseState
    data class Failed(val message: String) : DatabaseState
}

class DictionaryRepository(private val context: Context) {
    suspend fun open(): DatabaseState = withContext(Dispatchers.IO) {
        runCatching {
            val extracted = DatabaseExtractor.ensureDatabaseExists(context)
            if (!extracted) {
                return@withContext DatabaseState.Failed(
                    "离线词典解压失败，请检查手机存储空间是否充足（需至少 100 MB 可用空间）。",
                )
            }
            DatabaseState.Ready(DictionaryDatabase.open(context))
        }.getOrElse { DatabaseState.Failed("离线词典加载失败，请确认安装包包含 dict.db。") }
    }

    /** Delete the installed dictionary database so the next open re-extracts from the compressed asset. */
    suspend fun rebuild(): DatabaseState = withContext(Dispatchers.IO) {
        runCatching {
            context.deleteDatabase("dict.db")
            if (!DatabaseExtractor.ensureDatabaseExists(context)) {
                return@withContext DatabaseState.Failed("词典重建失败：无法解压离线词典。")
            }
            DatabaseState.Ready(DictionaryDatabase.open(context))
        }.getOrElse { DatabaseState.Failed("词典重建失败：${it.message}") }
    }
}
