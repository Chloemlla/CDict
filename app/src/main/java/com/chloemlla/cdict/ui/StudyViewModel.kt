package com.chloemlla.cdict.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.data.DatabaseState
import com.chloemlla.cdict.core.data.DictionaryDatabase
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.STUDY_STATUS_FREE
import com.chloemlla.cdict.core.data.STUDY_STATUS_LEARNED
import com.chloemlla.cdict.core.data.STUDY_STATUS_MASTERED
import com.chloemlla.cdict.core.data.StudyDatabase
import com.chloemlla.cdict.core.data.StudyWordEntity
import com.chloemlla.cdict.core.data.WordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

const val DAILY_GOAL_MIN = 10
const val DAILY_GOAL_MAX = 200
const val DAILY_GOAL_STEP = 10
const val DAILY_GOAL_DEFAULT = 20
private const val REVIEW_CAP = 50
private const val PREF_KEY_GOAL = "study_daily_goal"
private const val PREFS_NAME = "cdict_study_settings"

class StudyViewModel(context: Context) : ViewModel() {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<StudyScreenState>(StudyScreenState.Loading)
    val state: StateFlow<StudyScreenState> = _state.asStateFlow()

    private val _masteredIds = MutableStateFlow<Set<Long>>(emptySet())
    val masteredIds: StateFlow<Set<Long>> = _masteredIds.asStateFlow()

    private var dictDb: DictionaryDatabase? = null
    private var studyDb: StudyDatabase? = null

    private val reviewQueue = ArrayDeque<ReviewQuestion>()
    private val learnQueue = ArrayDeque<WordEntity>()
    private var learnedToday = mutableListOf<WordEntity>()
    private var todayDone = 0
    private var reviewTotal = 0

    private val today: String get() = LocalDate.now().toString()

    init {
        viewModelScope.launch {
            when (val result = DictionaryRepository(context).open()) {
                is DatabaseState.Ready -> {
                    dictDb = result.database
                    studyDb = StudyDatabase.open(context)
                    val dao = studyDb!!.studyDao()
                    viewModelScope.launch {
                        dao.masteredIds().collect { _masteredIds.value = it.toSet() }
                    }
                    reload()
                }
                is DatabaseState.Failed -> _state.value = StudyScreenState.NoDictionary(result.message)
                DatabaseState.Loading -> Unit
            }
        }
    }

    private fun dailyGoal(): Int = prefs.getInt(PREF_KEY_GOAL, DAILY_GOAL_DEFAULT).coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)

    fun setGoal(value: Int) {
        val goal = value.coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)
        prefs.edit().putInt(PREF_KEY_GOAL, goal).apply()
        viewModelScope.launch {
            val cur = _state.value as? StudyScreenState.Ready ?: return@launch
            when (cur.phase) {
                StudyPhase.LEARN, StudyPhase.FREE_PLAY -> {
                    topUpLearnQueue(isFree = cur.phase == StudyPhase.FREE_PLAY)
                    emit(cur.phase, card = learnQueue.firstOrNull())
                }
                else -> emit(cur.phase, card = cur.card)
            }
        }
    }

    /** Resets the whole in-memory session from persisted state, e.g. on entering the tab. */
    fun reload() {
        viewModelScope.launch {
            val dao = studyDb?.studyDao() ?: return@launch
            todayDone = dao.learnedTodayCount(today)
            refreshLearnedToday()
            val pending = dao.pendingReview(today, REVIEW_CAP)
            if (pending.isNotEmpty()) {
                buildReview(pending)
            } else if (todayDone >= dailyGoal()) {
                emit(StudyPhase.DONE, learnedList = learnedToday.toList())
            } else {
                buildLearn()
            }
        }
    }

    // ---- Review phase -----------------------------------------------------------------

    private suspend fun buildReview(pending: List<StudyWordEntity>) {
        val dictDao = dictDb?.dictionaryDao() ?: return
        reviewQueue.clear()
        reviewTotal = 0
        val ids = pending.map { it.wordId }
        val words = dictDao.wordsByIds(ids)
        val byId = words.groupBy { it.id }
        val distractorPool = dictDao.randomWords(400)
        for (entry in pending) {
            val word = byId[entry.wordId]?.firstOrNull() ?: continue
            val correctText = word.translation?.takeIf(String::isNotBlank) ?: word.definition?.takeIf(String::isNotBlank) ?: continue
            val pos = primaryPartOfSpeech(correctText)
            val distractors = buildReviewDistractors(pos, distractorPool, correctText)
            if (distractors.size < 3) continue
            val options = (listOf(correctText) + distractors).shuffled()
            reviewQueue.addLast(
                ReviewQuestion(
                    wordId = word.id,
                    english = word.word,
                    phonetic = phoneticsOf(word),
                    options = options,
                    correctIndex = options.indexOf(correctText),
                ),
            )
        }
        reviewTotal = reviewQueue.size
        if (reviewQueue.isEmpty()) startLearningPhase() else emit(StudyPhase.REVIEW, question = reviewQueue.firstOrNull())
    }

    fun answerReview(index: Int) {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase != StudyPhase.REVIEW) return
        val question = cur.question ?: return
        val chosen = question.options.getOrNull(index)
        emit(
            StudyPhase.REVIEW,
            question = question,
            feedback = ReviewFeedback(index == question.correctIndex, question.correctText, chosen),
        )
    }

    /**
     * Progresses past the displayed feedback. On a correct answer the question is marked
     * reviewed and drops off the queue; on a wrong answer it is requeued to the end so it
     * reappears before the review ends.
     */
    fun advanceAfterFeedback() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase != StudyPhase.REVIEW || cur.feedback == null) return
        val question = reviewQueue.firstOrNull() ?: return
        if (cur.feedback.correct) {
            val wordId = question.wordId
            viewModelScope.launch { studyDb?.studyDao()?.markReviewed(wordId) }
            reviewQueue.removeFirst()
            if (reviewQueue.isEmpty()) viewModelScope.launch { startLearningPhase() }
            else emit(StudyPhase.REVIEW, question = reviewQueue.first())
        } else {
            reviewQueue.removeFirst()
            reviewQueue.addLast(question)
            emit(StudyPhase.REVIEW, question = reviewQueue.first())
        }
    }

    // ---- Learning phase ----------------------------------------------------------------

    private suspend fun buildLearn() {
        topUpLearnQueue(isFree = false)
        emit(StudyPhase.LEARN, card = learnQueue.firstOrNull())
    }

    /**
     * Ensures the learn queue holds the remaining new words for the goal (or, in free play,
     * keeps a small pool handy). Tops up by the shortfall so raising the daily goal mid-day
     * pulls in the extra words without shorting the queue.
     */
    private suspend fun topUpLearnQueue(isFree: Boolean) {
        val dao = studyDb?.studyDao() ?: return
        val goal = dailyGoal()
        val occupied = learnQueue.mapTo(mutableSetOf()) { it.id }
        occupied.addAll(dao.allStudiedIds())
        val target = if (isFree) 12 else (goal - todayDone).coerceAtLeast(0)
        val need = (target - learnQueue.size).coerceAtLeast(0)
        if (need <= 0) return
        sampleNewWords(need, occupied).forEach { learnQueue.addLast(it) }
    }

    /** Reservior-style sampling that avoids huge SQLite IN-lists by filtering random batches. */
    private suspend fun sampleNewWords(need: Int, studied: Set<Long>): List<WordEntity> {
        val dictDao = dictDb?.dictionaryDao() ?: return emptyList()
        val result = mutableListOf<WordEntity>()
        val used = mutableSetOf<Long>()
        var attempts = 0
        while (result.size < need && attempts < 12) {
            for (word in dictDao.randomWords(600)) {
                if (result.size >= need) break
                if (word.id !in studied && used.add(word.id)) result.add(word)
            }
            attempts++
        }
        return result
    }

    fun markLearned() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        val card = cur.card ?: return
        if (cur.phase != StudyPhase.LEARN && cur.phase != StudyPhase.FREE_PLAY) return
        val isFree = cur.phase == StudyPhase.FREE_PLAY
        val wordId = card.id
        viewModelScope.launch {
            val dao = studyDb?.studyDao() ?: return@launch
            dao.upsert(
                StudyWordEntity(
                    wordId = wordId,
                    status = if (isFree) STUDY_STATUS_FREE else STUDY_STATUS_LEARNED,
                    learnedDate = if (isFree) null else today,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            if (!isFree) {
                todayDone++
                learnedToday.add(card)
            }
            learnQueue.remove(card)
            topUpLearnQueue(isFree)
            if (!isFree && todayDone >= dailyGoal()) {
                refreshLearnedToday()
                emit(StudyPhase.DONE, learnedList = learnedToday.toList())
            } else {
                emit(if (isFree) StudyPhase.FREE_PLAY else StudyPhase.LEARN, card = learnQueue.firstOrNull())
            }
        }
    }

    fun deferWord() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase != StudyPhase.LEARN && cur.phase != StudyPhase.FREE_PLAY) return
        val card = cur.card ?: return
        if (learnQueue.size > 1) {
            learnQueue.removeFirst()
            learnQueue.addLast(card)
        }
        emit(cur.phase, card = learnQueue.firstOrNull())
    }

    fun continueFreePlay() {
        viewModelScope.launch {
            topUpLearnQueue(isFree = true)
            emit(StudyPhase.FREE_PLAY, card = learnQueue.firstOrNull())
        }
    }

    fun exitFreePlay() {
        emit(StudyPhase.DONE, learnedList = learnedToday.toList())
    }

    // ---- Shared / dictionary integration --------------------------------------------------

    fun toggleMastered(wordId: Long) {
        viewModelScope.launch {
            val dao = studyDb?.studyDao() ?: return@launch
            if (wordId in _masteredIds.value) dao.delete(wordId)
            else dao.upsert(StudyWordEntity(wordId, STUDY_STATUS_MASTERED, null, false, System.currentTimeMillis(), System.currentTimeMillis()))
        }
    }

    // ---- State helpers ---------------------------------------------------------------

    private suspend fun startLearningPhase() {
        val goal = dailyGoal()
        refreshLearnedToday()
        if (todayDone >= goal) emit(StudyPhase.DONE, learnedList = learnedToday.toList())
        else buildLearn()
    }

    private suspend fun refreshLearnedToday() {
        val ids = studyDb?.studyDao()?.learnedTodayIds(today).orEmpty()
        learnedToday = if (ids.isEmpty()) mutableListOf()
        else dictDb?.dictionaryDao()?.wordsByIds(ids).orEmpty().toMutableList()
    }

    private fun emit(
        phase: StudyPhase,
        question: ReviewQuestion? = null,
        feedback: ReviewFeedback? = null,
        card: WordEntity? = null,
        learnedList: List<WordEntity> = learnedToday.toList(),
    ) {
        _state.value = StudyScreenState.Ready(
            dailyGoal = dailyGoal(),
            todayDone = todayDone,
            phase = phase,
            reviewTotal = reviewTotal,
            reviewRemaining = reviewQueue.size,
            question = question,
            feedback = feedback,
            card = card,
            queueRemaining = learnQueue.size,
            learnedToday = learnedList,
        )
    }

    private fun phoneticsOf(word: WordEntity): String? {
        val uk = word.phoneticUk?.takeIf(String::isNotBlank)?.let { "英  $it" }
        val us = word.phoneticUs?.takeIf(String::isNotBlank)?.let { "美  $it" }
        return listOfNotNull(uk, us).joinToString("  ·  ").ifBlank { null }
    }
}

class StudyViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StudyViewModel(context) as T
    }
}