package com.chloemlla.cdict.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 词库精确命中结果，只包含快速翻译弹窗要展示的字段。 */
data class QuickWord(
    val id: Long,
    val word: String,
    val phonetic: String?,
    val translation: String?,
    val definition: String?,
)

/**
 * 选中文字快速翻译弹窗使用的词条查询。
 *
 * 刻意绕开 Room：弹窗属于系统级轻量入口，既不能触发首次启动才做的近百兆词库解压，
 * 也不能在主界面已持有同一文件时再建一个可能触发迁移的 Room 实例。词库文件不存在
 * 时直接返回 null（视为未命中），由调用方只展示在线译文。
 */
object QuickWordLookup {
    private const val MAX_LOOKUP_LENGTH = 64

    /** 归一化候选词：原文本身与剥掉首尾标点的形式，两者都作为精确匹配键尝试。 */
    fun candidates(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LOOKUP_LENGTH) return emptyList()
        val stripped = trimmed.trim { !it.isLetterOrDigit() && !it.isWhitespace() && it != '-' && it != '\'' }
        return listOf(trimmed, stripped)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /** 依次用 [candidates] 里的键做精确匹配，返回第一个命中的词条；未命中或词库缺失返回 null。 */
    suspend fun find(context: Context, text: String): QuickWord? = withContext(Dispatchers.IO) {
        val keys = candidates(text)
        if (keys.isEmpty()) return@withContext null
        val file = DatabaseExtractor.databaseFile(context)
        if (!file.exists()) return@withContext null
        runCatching {
            // 必须以读写方式打开：Room 已把库头标记成 WAL，缺少 -shm 时只读连接根本打不开，
            // 弹窗会永远查不到本地词条。
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                keys.firstNotNullOfOrNull { key -> queryExact(db, key) }
            }
        }.getOrNull()
    }

    private fun queryExact(db: SQLiteDatabase, key: String): QuickWord? =
        db.rawQuery(
            "SELECT id, word, phoneticUs, phoneticUk, translation, definition " +
                "FROM words WHERE word = ? COLLATE NOCASE LIMIT 1",
            arrayOf(key),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                QuickWord(
                    id = cursor.getLong(0),
                    word = cursor.getString(1),
                    phonetic = cursor.getStringOrNull(2)?.takeIf(String::isNotBlank)
                        ?: cursor.getStringOrNull(3)?.takeIf(String::isNotBlank),
                    translation = cursor.getStringOrNull(4)?.takeIf(String::isNotBlank),
                    definition = cursor.getStringOrNull(5)?.takeIf(String::isNotBlank),
                )
            }
        }
}
