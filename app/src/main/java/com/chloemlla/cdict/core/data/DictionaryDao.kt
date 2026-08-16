package com.chloemlla.cdict.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Long

    @Query("SELECT * FROM words ORDER BY frequencyGroup, frequency, word LIMIT :limit OFFSET :offset")
    fun browse(limit: Int, offset: Int): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id")
    fun observeWord(id: Long): Flow<WordEntity?>

    @Query("SELECT * FROM words WHERE frequencyGroup = :group ORDER BY frequency, word LIMIT :limit OFFSET :offset")
    fun browseGroup(group: Int, limit: Int, offset: Int): Flow<List<WordEntity>>

    @Query("SELECT words.* FROM words JOIN word_search ON words.id = word_search.rowid WHERE word_search MATCH :query ORDER BY words.frequencyGroup, words.frequency, words.word LIMIT :limit")
    fun searchEnglish(query: String, limit: Int = 100): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE word LIKE '%' || :query || '%' OR translation LIKE '%' || :query || '%' ORDER BY frequencyGroup, frequency, word LIMIT :limit")
    fun searchChinese(query: String, limit: Int = 100): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE frequencyGroup = :group AND frequency < :frequency ORDER BY frequency DESC, word DESC LIMIT 1")
    suspend fun previous(group: Int, frequency: Int): WordEntity?

    @Query("SELECT * FROM words WHERE frequencyGroup = :group AND frequency > :frequency ORDER BY frequency, word LIMIT 1")
    suspend fun next(group: Int, frequency: Int): WordEntity?

    @Query("SELECT derived_terms.term FROM derived_terms WHERE wordId = :wordId ORDER BY term")
    suspend fun derivedTerms(wordId: Long): List<String>

    @Query("SELECT * FROM roots WHERE wordId = :wordId ORDER BY root")
    suspend fun roots(wordId: Long): List<RootEntity>

    @Query("SELECT sentences.* FROM sentences JOIN word_sentence_links ON sentences.id = word_sentence_links.sentenceId WHERE word_sentence_links.wordId = :wordId ORDER BY sentences.id LIMIT :limit OFFSET :offset")
    suspend fun sentences(wordId: Long, limit: Int, offset: Int): List<SentenceEntity>

    @Query("SELECT * FROM heatmap_entries WHERE wordId = :wordId ORDER BY period")
    suspend fun heatmap(wordId: Long): List<HeatmapEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)
}
