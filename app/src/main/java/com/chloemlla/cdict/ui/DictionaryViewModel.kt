package com.chloemlla.cdict.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.data.DatabaseState
import com.chloemlla.cdict.core.data.DictionaryDatabase
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.SentenceEntity
import com.chloemlla.cdict.core.data.WordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DictionaryScreenState {
    data object Loading : DictionaryScreenState
    data class Ready(val words: List<WordEntity>, val selected: WordEntity? = null, val query: String = "") : DictionaryScreenState
    data class Error(val message: String) : DictionaryScreenState
}

class DictionaryViewModel(private val repository: DictionaryRepository) : ViewModel() {
    private val _state = MutableStateFlow<DictionaryScreenState>(DictionaryScreenState.Loading)
    val state: StateFlow<DictionaryScreenState> = _state.asStateFlow()
    private var database: DictionaryDatabase? = null

    init {
        viewModelScope.launch {
            when (val result = repository.open()) {
                is DatabaseState.Ready -> {
                    database = result.database
                    result.database.dictionaryDao().browse(50, 0).collect { words ->
                        _state.value = DictionaryScreenState.Ready(words)
                    }
                }
                is DatabaseState.Failed -> _state.value = DictionaryScreenState.Error(result.message)
                DatabaseState.Loading -> Unit
            }
        }
    }

    fun search(query: String) {
        val db = database ?: return
        viewModelScope.launch {
            val flow = if (query.any { it.code > 127 }) {
                db.dictionaryDao().searchChinese(query)
            } else if (query.isBlank()) {
                db.dictionaryDao().browse(50, 0)
            } else {
                db.dictionaryDao().searchEnglish("${query.trim()}*")
            }
            flow.collect { words ->
                _state.value = DictionaryScreenState.Ready(words, query = query)
            }
        }
    }

    fun select(word: WordEntity) {
        val current = _state.value
        if (current is DictionaryScreenState.Ready) {
            _state.value = if (word.id == -1L) current.copy(selected = null) else current.copy(selected = word)
        }
    }
}

class DictionaryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DictionaryViewModel(DictionaryRepository(context)) as T
    }
}
