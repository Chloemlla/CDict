package com.chloemlla.cdict.core.data

import androidx.room.Dao
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

    @Query("SELECT * FROM words WHERE frequencyGroup = :group ORDER BY frequency, word LIMIT :limit OFFSET :offset")
    fun browseGroup(group: Int, limit: Int, offset: Int): Flow<List<WordEntity>>

    // Tag-aware cold-start browse used by the recommendation cold path when a curriculum
    // label is active: only entries carrying that label are offered as starters.
    @Query(
        "SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "AND frequencyGroup = :group ORDER BY frequency, word LIMIT :limit OFFSET :offset",
    )
    fun browseGroupFiltered(tag: String?, group: Int, limit: Int, offset: Int): Flow<List<WordEntity>>

    // 精确命中必须在 LIMIT 之前参与排序：查询词本身是低频词时，以它为前缀的高频词能把它挤出
    // 前 100 行，事后重排也救不回来。FTS 前缀表达式就是 `<原词>*`，剥掉尾部 * 即原词；带引号的
    // 复合表达式比不中任何词条，退化为原来的频次排序。
    @Query(
        "SELECT words.* FROM words JOIN word_search ON words.id = word_search.rowid " +
            "WHERE word_search MATCH :query " +
            "AND (:tag IS NULL OR INSTR(',' || words.curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "ORDER BY (words.word = RTRIM(:query, '*') COLLATE NOCASE) DESC, " +
            "words.frequencyGroup, words.frequency, words.word LIMIT :limit",
    )
    fun searchEnglish(query: String, tag: String? = null, limit: Int = 100): Flow<List<WordEntity>>

    // Typo-tolerance candidate pool (优化项): edit distance <= 2 implies the corrected word
    // and the query differ in length by at most 2, so bounding word length shrinks the
    // 49k-entry set to a small neighborhood before scanning. Sorted by core frequency first
    // so the most likely IELTS word wins ties.
    @Query("SELECT * FROM words WHERE length(word) BETWEEN :minLength AND :maxLength ORDER BY frequencyGroup, frequency, word LIMIT :limit")
    suspend fun wordsInLengthRange(minLength: Int, maxLength: Int, limit: Int): List<WordEntity>

    // ESCAPE 让调用方转义后的 % / _ 按字面匹配，否则用户输入的通配符会命中整张表。
    @Query(
        "SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "AND (word LIKE '%' || :query || '%' ESCAPE '\\' OR translation LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY frequencyGroup, frequency, word LIMIT :limit",
    )
    fun searchChinese(query: String, tag: String? = null, limit: Int = 100): Flow<List<WordEntity>>

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
    // unfiltered distractor options for the next-day review questions.
    @Query("SELECT * FROM words WHERE translation IS NOT NULL AND length(translation) > 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomWords(limit: Int): List<WordEntity>

    // Random sample over the whole corpus, filtered by an optional curriculum tag when the study or
    // recommendation feed is scoped to one label (used for cold-start fallback padding).
    @Query(
        "SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "AND translation IS NOT NULL AND length(translation) > 0 ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun randomWordsFiltered(tag: String?, limit: Int): List<WordEntity>

    // Random sample over the whole corpus, filtered by an optional curriculum tag and frequency band.
    // Used by Study/Recommendation blanket fallbacks when scoped band sampling runs dry.
    @Query(
        "SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "AND (:group IS NULL OR frequencyGroup = :group) AND translation IS NOT NULL AND length(translation) > 0 " +
            "ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun randomScoped(tag: String?, group: Int?, limit: Int): List<WordEntity>

    // Random sample restricted to a frequency band, used by the adaptive cold-start
    // gradient (core / high-frequency-extension / simple words) to pull recommendations
    // from the right difficulty neighbourhood of the user's estimated level.
    @Query("SELECT * FROM words WHERE frequencyGroup = :group AND translation IS NOT NULL AND length(translation) > 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomWordsInGroup(group: Int, limit: Int): List<WordEntity>

    // Tag-aware band sample: restricts the random draw to entries carrying a curriculum label
    // (e.g. 高中 3500 词 / 高中短语). A null tag disables the filter, matching the unfiltered call.
    @Query(
        "SELECT * FROM words WHERE (:tag IS NULL OR INSTR(',' || curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "AND frequencyGroup = :group AND translation IS NOT NULL AND length(translation) > 0 ORDER BY RANDOM() LIMIT :limit",
    )
    suspend fun randomWordsInGroupFiltered(tag: String?, group: Int, limit: Int): List<WordEntity>

    // Expansion pool (方案A): new words sharing a word root with already-studied words, so the
    // daily exploration feed extends from familiar vocabulary instead of the review backlog.
    @Query(
        "SELECT DISTINCT words.* FROM words JOIN roots ON words.id = roots.wordId " +
            "WHERE roots.root IN (:roots) ORDER BY words.frequencyGroup, words.frequency, words.word LIMIT :limit",
    )
    suspend fun wordsSharingRoots(roots: List<String>, limit: Int): List<WordEntity>

    // Tag-aware root expansion: only new words that both share a studied root AND also carry the
    // active curriculum label qualify, so a scoped feed never leaks in out-of-scope words.
    @Query(
        "SELECT DISTINCT words.* FROM words JOIN roots ON words.id = roots.wordId " +
            "WHERE (:tag IS NULL OR INSTR(',' || words.curriculumTags || ',', ',' || :tag || ',') > 0) " +
            "AND roots.root IN (:roots) ORDER BY words.frequencyGroup, words.frequency, words.word LIMIT :limit",
    )
    suspend fun wordsSharingRootsFiltered(tag: String?, roots: List<String>, limit: Int): List<WordEntity>

    // COLLATE NOCASE 让大小写无关的批量取词能走 idx_words_word_nocase；LOWER(word) 包在列上则索引失效。
    @Query("SELECT * FROM words WHERE word COLLATE NOCASE IN (:words)")
    suspend fun wordsByText(words: List<String>): List<WordEntity>

    @Query("SELECT value FROM metadata WHERE key = :key")
    suspend fun metadataValue(key: String): String?
}
