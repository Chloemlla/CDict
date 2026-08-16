package com.chloemlla.cdict.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.audio.PronunciationPlayer
import com.chloemlla.cdict.core.data.DatabaseState
import com.chloemlla.cdict.core.data.DictionaryDatabase
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.DictionaryUpdateManager
import com.chloemlla.cdict.core.data.HeatmapEntryEntity
import com.chloemlla.cdict.core.data.RootEntity
import com.chloemlla.cdict.core.data.SentenceEntity
import com.chloemlla.cdict.core.data.WordEntity
import com.chloemlla.cdict.core.search.SearchEngine
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
        val sortMode: SortMode = SortMode.Frequency,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val updateNeeded: Boolean = false,
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

    init {
        viewModelScope.launch {
            when (val result = repository.open()) {
                is DatabaseState.Ready -> {
                    val db = result.database
                    database = db
                    totalCount = db.dictionaryDao().count()
                    val updateNeeded = DictionaryUpdateManager.check(appContext, db)
                    val words = browsePage(db, SortMode.Frequency, 0)
                    _state.value = DictionaryScreenState.Ready(
                        words = words,
                        sortMode = SortMode.Frequency,
                        hasMore = words.size.toLong() < totalCount,
                        updateNeeded = updateNeeded,
                    )
                }
                is DatabaseState.Failed -> _state.value = DictionaryScreenState.Error(result.message)
                DatabaseState.Loading -> Unit
            }
        }
    }

    private suspend fun browsePage(
        db: DictionaryDatabase,
        mode: SortMode,
        offset: Int,
    ): List<WordEntity> =
        when (mode) {
            SortMode.Frequency -> db.dictionaryDao().browse(pageSize, offset)
            SortMode.Alphabetical -> db.dictionaryDao().browseAlphabetical(pageSize, offset)
            SortMode.AlphabeticalDesc -> db.dictionaryDao().browseAlphabeticalDesc(pageSize, offset)
        }.first()

    private fun currentSortMode(): SortMode =
        (_state.value as? DictionaryScreenState.Ready)?.sortMode ?: SortMode.Frequency

    fun setSortMode(mode: SortMode) {
        val db = database ?: return
        if (currentSortMode() == mode) return
        viewModelScope.launch {
            totalCount = db.dictionaryDao().count()
            val words = browsePage(db, mode, 0)
            _state.value = DictionaryScreenState.Ready(
                words = words,
                query = "",
                sortMode = mode,
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
            val newState = if (query.isBlank()) {
                totalCount = db.dictionaryDao().count()
                val mode = currentSortMode()
                val words = browsePage(db, mode, 0)
                DictionaryScreenState.Ready(
                    words = words,
                    query = "",
                    sortMode = mode,
                    hasMore = words.size.toLong() < totalCount,
                )
            } else if (query.any { it.code > 127 }) {
                DictionaryScreenState.Ready(
                    words = db.dictionaryDao().searchChinese(query).first(),
                    query = query,
                )
            } else {
                val trimmed = query.trim()
                val dao = db.dictionaryDao()
                val words = SearchEngine.reorderForSearch(
                    trimmed,
                    dao.searchEnglish("$trimmed*").first(),
                )
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
        // Flip the loading footer synchronously (no suspension in between) so the UI never re-requests.
        _state.value = state.copy(isLoadingMore = true)
        loadMoreInFlight = true
        viewModelScope.launch {
            try {
                val more = browsePage(db, sortMode, offset)
                val latest = _state.value as? DictionaryScreenState.Ready ?: return@launch
                // Discard stale results when a newer search or sort change superseded this page.
                if (latest.query.isNotBlank() || latest.sortMode != sortMode || latest.words.size != offset) return@launch
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
            _state.value = current.copy(selected = null, detail = null)
            return
        }
        _state.value = current.copy(selected = word, detail = null)
        val db = database
        if (db == null) return
        val dao = db.dictionaryDao()
        viewModelScope.launch {
            // Pre-fetch (PRD §3.4): warm the audio LRU cache for the word and its speakable
            // fragments while the detail page loads, so the first tap plays instantly.
            pronunciationPlayer.prefetch(word.word, Accent.US)
            pronunciationPlayer.prefetch(word.word, Accent.UK)
            val derivedTerms = dao.derivedTerms(word.id)
            val derivedTermWords = dao.wordsByText(derivedTerms.map { it.lowercase() })
                .associateBy { it.word.lowercase() }
            val detail = WordDetailData(
                derivedTerms = derivedTerms,
                derivedTermWords = derivedTermWords,
                roots = dao.roots(word.id),
                sentences = dao.sentences(word.id, limit = 10, offset = 0),
                heatmap = dao.heatmap(word.id),
            )
            detail.sentences.forEach { s -> s.english.takeIf(String::isNotBlank)?.let { pronunciationPlayer.prefetch(it, Accent.US) } }
            val latest = _state.value
            if (latest is DictionaryScreenState.Ready && latest.selected?.id == word.id) {
                _state.value = latest.copy(detail = detail)
            }
        }
    }

    /** Closes the open word detail, returning the dictionary tab to its browse list. */
    fun deselect() {
        val current = _state.value
        if (current is DictionaryScreenState.Ready && current.selected != null) {
            _state.value = current.copy(selected = null, detail = null)
        }
    }

    /** Rebuild the installed dictionary database from the bundled asset. */
    fun rebuildDictionary() {
        viewModelScope.launch {
            database?.close()
            database = null
            _state.value = DictionaryScreenState.Loading
            when (val result = repository.rebuild()) {
                is DatabaseState.Ready -> {
                    val db = result.database
                    database = db
                    totalCount = db.dictionaryDao().count()
                    val words = browsePage(db, SortMode.Frequency, 0)
                    _state.value = DictionaryScreenState.Ready(
                        words = words,
                        sortMode = SortMode.Frequency,
                        hasMore = words.size.toLong() < totalCount,
                        updateNeeded = false,
                    )
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
        pronunciationPlayer.play(word.word, accent)
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
