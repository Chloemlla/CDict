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
        runCatching { DatabaseState.Ready(DictionaryDatabase.open(context)) }
            .getOrElse { DatabaseState.Failed("离线词典加载失败，请确认安装包包含 dict.db。") }
    }

    /** Delete the installed dictionary database so Room re-copies from the bundled asset. */
    suspend fun rebuild(): DatabaseState = withContext(Dispatchers.IO) {
        runCatching {
            context.deleteDatabase("dict.db")
            DatabaseState.Ready(DictionaryDatabase.open(context))
        }.getOrElse { DatabaseState.Failed("词典重建失败：${it.message}") }
    }
}
