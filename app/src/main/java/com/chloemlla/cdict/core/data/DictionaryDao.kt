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

    // A curriculum tag is one label inside the comma-separated curriculumTags column;
    // INSTR with wrapped commas matches whole labels only, never substrings. A null tag
    // means "no curriculum filter" and the IS NULL arm returns every row.
    @Query("SELECT COUNT(*) FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0)")
    suspend fun countFiltered(tag: String?): Long

    @Query("SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) ORDER BY frequencyGroup, frequency, word LIMIT :limit OFFSET :offset")
    fun browse(limit: Int, offset: Int, tag: String?): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) ORDER BY word COLLATE NOCASE LIMIT :limit OFFSET :offset")
    fun browseAlphabetical(limit: Int, offset: Int, tag: String?): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) ORDER BY word COLLATE NOCASE DESC LIMIT :limit OFFSET :offset")
    fun browseAlphabeticalDesc(limit: Int, offset: Int, tag: String?): Flow<List<WordEntity>>

    // Distinct curriculum label sets present in the asset, so the filter menu stays in sync
    // with whatever tags the publishing pipeline applies (高中 3500 词, future lists, ...).
    @Query("SELECT DISTINCT curriculumTags FROM words WHERE curriculumTags IS NOT NULL AND length(curriculumTags) > 0")
    suspend fun distinctCurriculumTags(): List<String>

    @Query("SELECT * FROM words WHERE id = :id")
    fun observeWord(id: Long): Flow<WordEntity?>

    @Query("SELECT * FROM words WHERE frequencyGroup = :group ORDER BY frequency, word LIMIT :limit OFFSET :offset")
    fun browseGroup(group: Int, limit: Int, offset: Int): Flow<List<WordEntity>>

    @Query("SELECT words.* FROM words JOIN word_search ON words.id = word_search.rowid WHERE word_search MATCH :query ORDER BY words.frequencyGroup, words.frequency, words.word LIMIT :limit")
    fun searchEnglish(query: String, limit: Int = 100): Flow<List<WordEntity>>

    // Typo-tolerance candidate pool (优化项): edit distance <= 2 implies the corrected word
    // and the query differ in length by at most 2, so bounding word length shrinks the
    // 49k-entry set to a small neighborhood before scanning. Sorted by core frequency first
    // so the most likely IELTS word wins ties.
    @Query("SELECT * FROM words WHERE length(word) BETWEEN :minLength AND :maxLength ORDER BY frequencyGroup, frequency, word LIMIT :limit")
    suspend fun wordsInLengthRange(minLength: Int, maxLength: Int, limit: Int): List<WordEntity>

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

    @Query("SELECT * FROM word_relations WHERE wordId = :wordId ORDER BY relationType, targetWord")
    suspend fun relations(wordId: Long): List<WordRelationEntity>

    @Query("SELECT * FROM word_forms WHERE wordId = :wordId ORDER BY formIndex")
    suspend fun forms(wordId: Long): List<WordFormEntity>

    @Query("SELECT * FROM etymologies WHERE wordId = :wordId ORDER BY etymologyIndex")
    suspend fun etymologies(wordId: Long): List<EtymologyEntity>

    @Query("SELECT * FROM study_notes WHERE wordId = :wordId ORDER BY noteIndex")
    suspend fun studyNotes(wordId: Long): List<StudyNoteEntity>

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun wordsByIds(ids: List<Long>): List<WordEntity>

    // Random sample of words with a non-empty Chinese translation, used to draw
    // distractor options for the next-day review questions.
    @Query("SELECT * FROM words WHERE translation IS NOT NULL AND length(translation) > 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomWords(limit: Int): List<WordEntity>

    // Random sample restricted to a frequency band, used by the adaptive cold-start
    // gradient (core / high-frequency-extension / simple words) to pull recommendations
    // from the right difficulty neighbourhood of the user's estimated level.
    @Query("SELECT * FROM words WHERE frequencyGroup = :group AND translation IS NOT NULL AND length(translation) > 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomWordsInGroup(group: Int, limit: Int): List<WordEntity>

    // Expansion pool (方案A): new words sharing a word root with already-studied words, so the
    // daily exploration feed extends from familiar vocabulary instead of the review backlog.
    @Query(
        "SELECT DISTINCT words.* FROM words JOIN roots ON words.id = roots.wordId " +
            "WHERE roots.root IN (:roots) ORDER BY words.frequencyGroup, words.frequency, words.word LIMIT :limit",
    )
    suspend fun wordsSharingRoots(roots: List<String>, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE LOWER(word) IN (:words)")
    suspend fun wordsByText(words: List<String>): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Query("SELECT value FROM metadata WHERE key = :key")
    suspend fun metadataValue(key: String): String?
}
