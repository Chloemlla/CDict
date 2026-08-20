package com.chloemlla.cdict.ui

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.audio.PronunciationPlayer
import com.chloemlla.cdict.core.data.DatabaseState
import com.chloemlla.cdict.core.data.DictionaryDatabase
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.DictionaryUpdateManager
import com.chloemlla.cdict.core.data.EtymologyEntity
import com.chloemlla.cdict.core.data.HeatmapEntryEntity
import com.chloemlla.cdict.core.data.RootEntity
import com.chloemlla.cdict.core.data.SentenceEntity
import com.chloemlla.cdict.core.data.StudyNoteEntity
import com.chloemlla.cdict.core.data.WordEntity
import com.chloemlla.cdict.core.data.WordFormEntity
import com.chloemlla.cdict.core.data.WordRelationEntity
import com.chloemlla.cdict.core.search.SearchEngine
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WordDetailData(
    val derivedTerms: List<String> = emptyList(),
    val derivedTermWords: Map<String, WordEntity> = emptyMap(),
    val roots: List<RootEntity> = emptyList(),
    val sentences: List<SentenceEntity> = emptyList(),
    val heatmap: List<HeatmapEntryEntity> = emptyList(),
    val relations: List<WordRelationEntity> = emptyList(),
    val relationWords: Map<String, WordEntity> = emptyMap(),
    val forms: List<WordFormEntity> = emptyList(),
    val etymologies: List<EtymologyEntity> = emptyList(),
    val studyNotes: List<StudyNoteEntity> = emptyList(),
)

enum class SortMode(val label: String) {
    Frequency("按频率"),
    Alphabetical("按字母"),
    AlphabeticalDesc("字母倒序"),
}

private const val SEARCH_DEBOUNCE_MS = 300L

sealed interface DictionaryScreenState {
    data object Loading : DictionaryScreenState
    data class Ready(
        val words: List<WordEntity>,
        val selected: WordEntity? = null,
        val query: String = "",
        val suggestion: WordEntity? = null,
        val detail: WordDetailData? = null,
        val detailError: String? = null,
        val sortMode: SortMode = SortMode.Frequency,
        val curriculumTag: String? = null,
        val availableCurriculumTags: List<String> = emptyList(),
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val updateNeeded: Boolean = false,
        val playingKey: String? = null,
    ) : DictionaryScreenState
    data class Error(val message: String) : DictionaryScreenState
}

class DictionaryViewModel(
    private val repository: DictionaryRepository,
    private val pronunciationPlayer: PronunciationPlayer,
    private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow<DictionaryScreenState>(DictionaryScreenState.Loading)
    val state: StateFlow<DictionaryScreenState> = _state.asStateFlow()
    private var database: DictionaryDatabase? = null
    private val pageSize = 100
    private var totalCount = 0L
    private var loadMoreInFlight = false
    private var searchJob: Job? = null
    // Distinct curriculum labels present in the asset, loaded once per database open so the
    // filter menu reflects whatever tags the publishing pipeline applied (高中 3500 词, ...).
    private var availableTags: List<String> = emptyList()
    // Words whose detail is still open underneath the current one (派生词「前往」跳转等),
    // so back walks back through details instead of dumping straight onto the browse list.
    private val detailStack = ArrayDeque<WordEntity>()
    // 词库就绪信号：外部入口（快速翻译弹窗的「前往」）可能早于首次加载完成到达，
    // 需要排队等待而不是被静默丢弃。
    private val databaseReady = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            when (val result = repository.open()) {
                is DatabaseState.Ready -> {
                    val db = result.database
                    database = db
                    totalCount = db.dictionaryDao().count()
                    availableTags = loadAvailableTags(db)
                    val updateNeeded = DictionaryUpdateManager.check(appContext, db)
                    val words = browsePage(db, SortMode.Frequency, null, 0)
                    _state.value = DictionaryScreenState.Ready(
                        words = words,
                        sortMode = SortMode.Frequency,
                        availableCurriculumTags = availableTags,
                        hasMore = words.size.toLong() < totalCount,
                        updateNeeded = updateNeeded,
                    )
                    databaseReady.value = true
                }
                is DatabaseState.Failed -> _state.value = DictionaryScreenState.Error(result.message)
                DatabaseState.Loading -> Unit
            }
        }
    }

    private suspend fun browsePage(
        db: DictionaryDatabase,
        mode: SortMode,
        tag: String?,
        offset: Int,
    ): List<WordEntity> =
        when (mode) {
            SortMode.Frequency -> db.dictionaryDao().browse(pageSize, offset, tag)
            SortMode.Alphabetical -> db.dictionaryDao().browseAlphabetical(pageSize, offset, tag)
            SortMode.AlphabeticalDesc -> db.dictionaryDao().browseAlphabeticalDesc(pageSize, offset, tag)
        }.first()

    private suspend fun browseCount(db: DictionaryDatabase, tag: String?): Long =
        if (tag == null) db.dictionaryDao().count() else db.dictionaryDao().countFiltered(tag)

    private suspend fun loadAvailableTags(db: DictionaryDatabase): List<String> =
        db.dictionaryDao().distinctCurriculumTags()
            .flatMap { raw -> raw.split(',', '，').map(String::trim) }
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()

    private fun currentReady(): DictionaryScreenState.Ready? =
        _state.value as? DictionaryScreenState.Ready

    private fun currentSortMode(): SortMode =
        currentReady()?.sortMode ?: SortMode.Frequency

    private fun currentCurriculumTag(): String? = currentReady()?.curriculumTag

    fun setSortMode(mode: SortMode) {
        val db = database ?: return
        if (currentSortMode() == mode) return
        viewModelScope.launch {
            val tag = currentCurriculumTag()
            totalCount = browseCount(db, tag)
            val words = browsePage(db, mode, tag, 0)
            _state.value = DictionaryScreenState.Ready(
                words = words,
                query = "",
                sortMode = mode,
                curriculumTag = tag,
                availableCurriculumTags = availableTags,
                hasMore = words.size.toLong() < totalCount,
            )
        }
    }

    /** Restricts the browse list to one curriculum label (e.g. 高中 3500 词); null clears the filter. */
    fun setCurriculumTag(tag: String?) {
        val db = database ?: return
        if (currentCurriculumTag() == tag) return
        viewModelScope.launch {
            val mode = currentSortMode()
            totalCount = browseCount(db, tag)
            val words = browsePage(db, mode, tag, 0)
            _state.value = DictionaryScreenState.Ready(
                words = words,
                query = "",
                sortMode = mode,
                curriculumTag = tag,
                availableCurriculumTags = availableTags,
                hasMore = words.size.toLong() < totalCount,
            )
        }
    }

    fun search(query: String) {
        val db = database ?: return
        // Debounce rapid keystrokes: cancel the previous query so only the latest text runs,
        // avoiding a storm of DB reads and stale-result flicker while the user keeps typing.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val tag = currentCurriculumTag()
            val mode = currentSortMode()
            val newState = if (query.isBlank()) {
                totalCount = browseCount(db, tag)
                val words = browsePage(db, mode, tag, 0)
                DictionaryScreenState.Ready(
                    words = words,
                    query = "",
                    sortMode = mode,
                    curriculumTag = tag,
                    availableCurriculumTags = availableTags,
                    hasMore = words.size.toLong() < totalCount,
                )
            } else if (query.any { it.code > 127 }) {
                DictionaryScreenState.Ready(
                    words = db.dictionaryDao().searchChinese(query).first(),
                    query = query,
                    suggestion = null,
                    sortMode = mode,
                    curriculumTag = tag,
                    availableCurriculumTags = availableTags,
                )
            } else {
                val trimmed = query.trim()
                val dao = db.dictionaryDao()
                val words = try {
                    SearchEngine.reorderForSearch(
                        trimmed,
                        dao.searchEnglish(SearchEngine.ftsPrefixQuery(trimmed)).first(),
                    )
                } catch (e: SQLiteException) {
                    // FTS4 rejects operator sequences the sanitizer cannot fully anticipate;
                    // degrade to a plain LIKE substring scan instead of crashing the search.
                    SearchEngine.reorderForSearch(trimmed, dao.searchChinese(query).first())
                }
                // Typo tolerance (PRD §3.1): nothing matched on the headword prefix, so look
                // for a close neighbour (edit distance <= 2) within a length-bounded pool and
                // offer it as a "did you mean?" suggestion. Skipped once a real match exists.
                val suggestion =
                    if (words.isNotEmpty()) null
                    else dao.wordsInLengthRange(
                        minLength = maxOf(1, trimmed.length - 2),
                        maxLength = trimmed.length + 2,
                        limit = 300,
                    ).let { pool -> SearchEngine.suggest(trimmed, pool) }
                DictionaryScreenState.Ready(
                    words = words,
                    query = query,
                    suggestion = suggestion,
                    sortMode = mode,
                    curriculumTag = tag,
                    availableCurriculumTags = availableTags,
                )
            }
            _state.value = newState
        }
    }

    fun loadMore() {
        val state = _state.value as? DictionaryScreenState.Ready ?: return
        if (state.query.isNotBlank() || !state.hasMore || loadMoreInFlight) return
        val db = database ?: return
        val offset = state.words.size
        val sortMode = state.sortMode
        val tag = state.curriculumTag
        // Capture the list instance live when this page was requested; every list-resetting
        // operation (search/sort/filter) builds a brand-new list, so referential equality is a
        // reliable staleness signal that survives a reset which happens to produce the same size.
        val baseWords = state.words
        // Flip the loading footer synchronously (no suspension in between) so the UI never re-requests.
        _state.value = state.copy(isLoadingMore = true)
        loadMoreInFlight = true
        viewModelScope.launch {
            try {
                val more = browsePage(db, sortMode, tag, offset)
                val latest = _state.value as? DictionaryScreenState.Ready ?: return@launch
                // Discard stale results when a newer search, sort or filter change superseded this page.
                if (latest.words !== baseWords) return@launch
                val merged = latest.words + more
                _state.value = latest.copy(
                    words = merged,
                    hasMore = merged.size.toLong() < totalCount,
                    isLoadingMore = false,
                )
            } finally {
                loadMoreInFlight = false
            }
        }
    }

    fun select(word: WordEntity) {
        val current = _state.value
        if (current !is DictionaryScreenState.Ready) return
        if (word.id == -1L) {
            detailStack.clear()
            _state.value = current.copy(selected = null, detail = null)
            return
        }
        // A fresh selection (browse list tap or cross-tab jump) starts a new detail session.
        detailStack.clear()
        openWord(word)
    }

    /**
     * 外部入口（快速翻译弹窗的「前往」）要求打开的词条：等词库就绪后做精确匹配并直接
     * 展开详情；词库未收录时退化为把原文填进搜索，让用户看到近似结果而不是空白页。
     */
    fun openExternalWord(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            databaseReady.first { it }
            val dao = database?.dictionaryDao() ?: return@launch
            val match = dao.wordsByText(listOf(trimmed.lowercase())).firstOrNull()
            if (match != null) select(match) else search(trimmed)
        }
    }

    /** Opens a derived-term headword, keeping the current detail as the back destination. */
    fun openDerivedWord(word: WordEntity) {
        val current = _state.value
        if (current !is DictionaryScreenState.Ready || word.id == -1L) return
        current.selected?.takeIf { it.id != word.id }?.let { detailStack.addLast(it) }
        openWord(word)
    }

    private fun openWord(word: WordEntity) {
        val current = _state.value
        if (current !is DictionaryScreenState.Ready) return
        _state.value = current.copy(selected = word, detail = null, detailError = null)
        val db = database
        if (db == null) return
        val dao = db.dictionaryDao()
        viewModelScope.launch {
            try {
                // Pre-fetch (PRD §3.4): warm the audio LRU cache for the word and its speakable
                // fragments while the detail page loads, so the first tap plays instantly.
                pronunciationPlayer.prefetch(word.word, Accent.US)
                pronunciationPlayer.prefetch(word.word, Accent.UK)
                val derivedTerms = dao.derivedTerms(word.id)
                val derivedTermWords = dao.wordsByText(derivedTerms.map { it.lowercase() })
                    .associateBy { it.word.lowercase() }
                val relations = dao.relations(word.id)
                val relationWords = dao.wordsByText(relations.map { it.targetWord.lowercase() })
                    .associateBy { it.word.lowercase() }
                val detail = WordDetailData(
                    derivedTerms = derivedTerms,
                    derivedTermWords = derivedTermWords,
                    roots = dao.roots(word.id),
                    sentences = dao.sentences(word.id, limit = 10, offset = 0),
                    heatmap = dao.heatmap(word.id),
                    relations = relations,
                    relationWords = relationWords,
                    forms = dao.forms(word.id),
                    etymologies = dao.etymologies(word.id),
                    studyNotes = dao.studyNotes(word.id),
                )
                detail.sentences.forEach { s -> s.english.takeIf(String::isNotBlank)?.let { pronunciationPlayer.prefetch(it, Accent.US) } }
                val latest = _state.value
                if (latest is DictionaryScreenState.Ready && latest.selected?.id == word.id) {
                    _state.value = latest.copy(detail = detail)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed detail load must not leave the page stuck on "loading": surface an
                // explicit error with a retry entry point instead of a perpetual spinner.
                val latest = _state.value
                if (latest is DictionaryScreenState.Ready && latest.selected?.id == word.id) {
                    _state.value = latest.copy(detail = null, detailError = "词条详情加载失败，请重试")
                }
            }
        }
    }

    /**
     * Closes the current word detail. When a detail was reached via a 派生词「前往」jump,
     * returns to the previous detail; otherwise closes back onto the browse list.
     * @return true if a detail is still showing after walking back, false if fully closed.
     */
    fun deselect(): Boolean {
        val current = _state.value
        if (current !is DictionaryScreenState.Ready || current.selected == null) return false
        if (detailStack.isEmpty()) {
            _state.value = current.copy(selected = null, detail = null)
            return false
        }
        openWord(detailStack.removeLast())
        return true
    }

    /** Rebuild the installed dictionary database from the bundled asset. */
    fun rebuildDictionary() {
        viewModelScope.launch {
            database?.close()
            database = null
            detailStack.clear()
            databaseReady.value = false
            _state.value = DictionaryScreenState.Loading
            when (val result = repository.rebuild()) {
                is DatabaseState.Ready -> {
                    val db = result.database
                    database = db
                    totalCount = db.dictionaryDao().count()
                    availableTags = loadAvailableTags(db)
                    val words = browsePage(db, SortMode.Frequency, null, 0)
                    _state.value = DictionaryScreenState.Ready(
                        words = words,
                        sortMode = SortMode.Frequency,
                        availableCurriculumTags = availableTags,
                        hasMore = words.size.toLong() < totalCount,
                        updateNeeded = false,
                    )
                    databaseReady.value = true
                    DictionaryUpdateManager.markReconciled(appContext)
                }
                is DatabaseState.Failed -> _state.value = DictionaryScreenState.Error(result.message)
                DatabaseState.Loading -> Unit
            }
        }
    }

    /** Dismiss the update prompt without rebuilding. */
    fun dismissUpdate() {
        DictionaryUpdateManager.markReconciled(appContext)
        val current = _state.value
        if (current is DictionaryScreenState.Ready) {
            _state.value = current.copy(updateNeeded = false)
        }
    }

    fun playPronunciation(word: WordEntity, accent: Accent) {
        val key = "${word.id}:${accent.name}"
        val current = _state.value as? DictionaryScreenState.Ready ?: return
        // Toggle: clicking the button that is currently playing stops it.
        if (current.playingKey == key) {
            pronunciationPlayer.stop()
            _state.value = current.copy(playingKey = null)
            return
        }
        // Auto-clear the playing state when this audio finishes naturally.
        pronunciationPlayer.onCompletion = {
            val s = _state.value as? DictionaryScreenState.Ready
            if (s != null) {
                _state.value = s.copy(playingKey = null)
            }
        }
        pronunciationPlayer.play(word.word, accent)
        _state.value = current.copy(playingKey = key)
    }

    override fun onCleared() {
        database?.close()
        database = null
        pronunciationPlayer.release()
        super.onCleared()
    }
}

class DictionaryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DictionaryViewModel(
            DictionaryRepository(context),
            PronunciationPlayer(context),
            context.applicationContext,
        ) as T
    }
}
