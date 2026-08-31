package com.chloemlla.cdict.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed interface DatabaseState {
    data object Loading : DatabaseState
    data class Ready(val database: DictionaryDatabase) : DatabaseState
    data class Failed(val message: String) : DatabaseState
}

class DictionaryRepository(private val context: Context) {
    suspend fun open(): DatabaseState = withContext(Dispatchers.IO) {
        runCatching {
            val extraction = DatabaseExtractor.ensureDatabaseExists(context)
            if (extraction is ExtractionResult.Failure) {
                return@withContext DatabaseState.Failed(failureMessage(extraction))
            }
            DatabaseState.Ready(openAndTouch())
        }.getOrElse { DatabaseState.Failed(openFailureMessage(it)) }
    }

    /** Closes the shared instance, deletes the installed database and extracts it again. */
    suspend fun rebuild(): DatabaseState = withContext(Dispatchers.IO) {
        runCatching {
            DictionaryDatabase.closeInstance()
            context.deleteDatabase("dict.db")
            val extraction = DatabaseExtractor.ensureDatabaseExists(context)
            if (extraction is ExtractionResult.Failure) {
                return@withContext DatabaseState.Failed("词典重建失败：${failureMessage(extraction)}")
            }
            val database = openAndTouch()
            generationState.update { it + 1 }
            DatabaseState.Ready(database)
        }.getOrElse { DatabaseState.Failed("词典重建失败：${it.message ?: "未知错误"}") }
    }

    /**
     * Room opens the file lazily, so a schema or IO failure would otherwise surface in whichever
     * screen runs the first query instead of here. Force the open while the caller can still
     * report it as a recoverable failure.
     */
    private fun openAndTouch(): DictionaryDatabase =
        DictionaryDatabase.open(context).also { it.openHelper.writableDatabase }

    private fun failureMessage(failure: ExtractionResult.Failure): String = when (failure) {
        is ExtractionResult.InsufficientStorage ->
            "离线词典解压失败：存储空间不足，请清理出至少 ${failure.requiredBytes / (1024 * 1024)} MB 可用空间后重试。"
        ExtractionResult.AssetMissing ->
            "安装包缺少离线词典数据，请重新下载安装完整的安装包。"
        is ExtractionResult.Corrupted ->
            "离线词典数据损坏，请重新下载安装" + (failure.detail?.let { "（$it）" } ?: "") + "。"
    }

    private fun openFailureMessage(cause: Throwable): String =
        "离线词典打开失败，请重建词库；若仍失败请重新安装。${cause.message?.let { "（$it）" }.orEmpty()}"

    companion object {
        private val generationState = MutableStateFlow(0)

        /** Incremented after every successful [rebuild] so subscribers can re-resolve their dao. */
        val generation: StateFlow<Int> = generationState.asStateFlow()

        /** The already-opened database, or null when nothing opened it yet; never triggers extraction. */
        val current: DictionaryDatabase?
            get() = DictionaryDatabase.openedInstance
    }
}
