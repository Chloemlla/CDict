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
import com.chloemlla.cdict.core.data.STUDY_STATUS_LEARNING
import com.chloemlla.cdict.core.data.STUDY_STATUS_MASTERED
import com.chloemlla.cdict.core.data.STUDY_STATUS_REVIEW
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

// Adaptive Spaced Repetition ladder (优化项二): repetitions map to 1 / 3 / 7 / 15 / 30 days.
private val REVIEW_INTERVALS = intArrayOf(1, 3, 7, 15, 30)
private const val MASTER_REPETITIONS = 5
private const val EASE_DELTA = 0.15
private const val EASE_MAX = 3.0

// Ebbinghaus decay weighting (PRD §3.3): a per-band multiplier bends the base ladder by the
// word's IELTS frequency. DAO frequencyGroup runs 1 (core 真题高频) .. 7 (生僻低频):
//   group 1 -> 0.5  (core words reviewed on a tighter cadence — high priority)
//   group 7 -> 1.7  (obscure words stretch their intervals — focus stays on core)
// This maps the base interval to base * MULT_MIN + t * (MULT_MAX - MULT_MIN), clamped >= 1 day.
private const val GROUP_DECAY_MULT_MIN = 0.5
private const val GROUP_DECAY_MULT_MAX = 1.7

// Anti-overwhelmed cap (PRD §2.2 断刷截断): a long absence must not dump the whole backlog
// in one session. The overdue tail stays due and drains over the following days; the cap is
// the smaller of the goal-scaled budget and the hard 50-question truncation from the spec.
private const val REVIEW_CAP_MULTIPLIER = 2.5
private const val REVIEW_ABSENCE_HARD_CAP = 50

// Error Attribution thresholds (优化项五), in milliseconds.
private const val CONFUSION_MAX_MS = 1500L
private const val UNKNOWN_MIN_MS = 6000L

// Adaptive cold-start gradient (优化项四): a guess at the learner's boundary difficulty
// band, feeding the 60%/30%/10% recommendation split.
private const val DEFAULT_BOUNDARY_GROUP = 3

// Smart-cool-down insertion slot for 稍后再看 (优化项三).
private const val DEFER_INSERT_SLOT = 4

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

    // Wall-clock when the current review question last became answerable; feeds the
    // Error-Attribution engine's hesitation measurement.
    private var questionShownAt = 0L

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
            val cap = (dailyGoal() * REVIEW_CAP_MULTIPLIER).toInt()
                .coerceAtMost(REVIEW_ABSENCE_HARD_CAP)
                .coerceAtLeast(DAILY_GOAL_MIN)
            val pending = dao.pendingReview(today, cap)
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
        // Prioritise core IELTS words first (PRD §3.3): the review batch is drawn from the
        // decayed pool but re-ordered by frequencyGroup so high-frequency words surface
        // before obscure ones when the backlog exceeds a single session.
        val ordered = pending.sortedBy { byId[it.wordId]?.firstOrNull()?.frequencyGroup ?: Int.MAX_VALUE }
        for (entry in ordered) {
            val word = byId[entry.wordId]?.firstOrNull() ?: continue
            val correctText = word.translation?.takeIf(String::isNotBlank) ?: word.definition?.takeIf(String::isNotBlank) ?: continue
            val distractors = buildReviewDistractors(word, distractorPool)
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
        if (index != question.correctIndex) {
            // Error-Attribution Engine (优化项五): classify the miss by hesitation time and
            // tailor the retry. A fast wrong answer means orthographic/blind confusion (the
            // same option set is pinned for a focused re-discrimination); a slow wrong answer
            // signals an unfamiliar word, so the retry re-shows the 释义 card first.
            val elapsed = System.currentTimeMillis() - questionShownAt
            val retry = when {
                elapsed < CONFUSION_MAX_MS -> question.copy(attempt = question.attempt + 1, confusionRetry = true)
                elapsed > UNKNOWN_MIN_MS -> question.copy(attempt = question.attempt + 1, forceReveal = true)
                else -> question.copy(attempt = question.attempt + 1)
            }
            reviewQueue[0] = retry
            emit(StudyPhase.REVIEW, question = retry, feedback = ReviewFeedback(false, question.correctText, chosen))
        } else {
            emit(StudyPhase.REVIEW, question = question, feedback = ReviewFeedback(true, question.correctText, chosen))
        }
    }

    /**
     * Progresses past the displayed feedback. A correct answer promotes the word up the
     * adaptive spacing ladder; a wrong answer requeues the (attribution-tailored) retry to
     * the end so it reappears before the review ends.
     */
    fun advanceAfterFeedback() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase != StudyPhase.REVIEW || cur.feedback == null) return
        val question = reviewQueue.firstOrNull() ?: return
        if (cur.feedback.correct) {
            viewModelScope.launch { scheduleReview(question.wordId) }
            reviewQueue.removeFirst()
            if (reviewQueue.isEmpty()) viewModelScope.launch { startLearningPhase() }
            else emit(StudyPhase.REVIEW, question = reviewQueue.first())
        } else {
            reviewQueue.removeFirst()
            reviewQueue.addLast(question)
            emit(StudyPhase.REVIEW, question = reviewQueue.first())
        }
    }

    /**
     * Adaptive Spaced Repetition write: advance the memory state on a correct review.
     * The base 1/3/7/15/30-day ladder is bent by the word's IELTS frequency band (PRD §3.3)
     * so core words are re-tested sooner and obscure words spread further apart.
     */
    private suspend fun scheduleReview(wordId: Long) {
        val dao = studyDb?.studyDao() ?: return
        val row = dao.word(wordId) ?: return
        val slot = row.repetitions.coerceIn(0, REVIEW_INTERVALS.lastIndex)
        val interval = decayedInterval(REVIEW_INTERVALS[slot], frequencyGroupOf(wordId))
        val repetitions = row.repetitions + 1
        val ease = (row.ease + EASE_DELTA).coerceAtMost(EASE_MAX)
        val status = if (repetitions >= MASTER_REPETITIONS) STUDY_STATUS_MASTERED else STUDY_STATUS_REVIEW
        dao.schedule(wordId, status, plusDays(interval), ease, repetitions, interval)
    }

    /** Linear interpolation of the decay multiplier across the 7 frequency bands. */
    private fun frequencyGroupOf(wordId: Long): Int {
        val group = dictDb?.dictionaryDao()?.wordsByIds(listOf(wordId))?.firstOrNull()?.frequencyGroup ?: 3
        return group.coerceIn(1, 7)
    }

    private fun decayedInterval(baseDays: Int, frequencyGroup: Int): Int {
        val t = (frequencyGroup - 1) / 6.0 // 0.0 (core) .. 1.0 (obscure)
        val mult = GROUP_DECAY_MULT_MIN + t * (GROUP_DECAY_MULT_MAX - GROUP_DECAY_MULT_MIN)
        return maxOf(1, (baseDays * mult).toInt())
    }

    /** Marks the current question as just presented, restarting the hesitation clock. */
    fun noteQuestionPresented() {
        questionShownAt = System.currentTimeMillis()
    }

    /**
     * Developer backdoor: launches the next-day review exam with a randomly sampled word so
     * the review UI can be exercised without a real due queue. Invisible to normal use
     * (only reachable via the five-tap developer panel in the study top bar).
     */
    fun debugLaunchReview() {
        viewModelScope.launch {
            val dictDao = dictDb?.dictionaryDao() ?: return@launch
            val pool = dictDao.randomWords(300)
            val target = pool.firstOrNull() ?: return@launch
            val correctText = target.translation?.takeIf(String::isNotBlank)
                ?: target.definition?.takeIf(String::isNotBlank) ?: return@launch
            val distractors = buildReviewDistractors(target, pool)
            if (distractors.size < 3) return@launch
            val options = (listOf(correctText) + distractors).shuffled()
            reviewQueue.clear()
            reviewTotal = 1
            reviewQueue.addLast(
                ReviewQuestion(
                    wordId = target.id,
                    english = target.word,
                    phonetic = phoneticsOf(target),
                    options = options,
                    correctIndex = options.indexOf(correctText),
                ),
            )
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

    /**
     * Adaptive cold-start recommendation (优化项四): pulls the [need] new words along the
     * 60% core / 30% high-frequency-extension / 10% simple-word gradient around the learner's
     * boundary difficulty band, then pads any shortfall from the general unstudied pool.
     * Reservoir-style sampling keeps the studied set out of large SQLite IN-lists.
     */
    private suspend fun sampleNewWords(need: Int, occupied: Set<Long>): List<WordEntity> {
        if (need <= 0) return emptyList()
        val used = occupied.toMutableSet()
        val result = mutableListOf<WordEntity>()
        val core = need * 6 / 10
        val expand = need * 3 / 10
        val easy = need - core - expand
        result += sampleGroup(core, DEFAULT_BOUNDARY_GROUP, used)
        result += sampleGroup(expand, minOf(DEFAULT_BOUNDARY_GROUP + 1, 7), used)
        result += sampleGroup(easy, maxOf(DEFAULT_BOUNDARY_GROUP - 1, 1), used)
        if (result.size < need) {
            val dictDao = dictDb?.dictionaryDao() ?: return result
            var attempts = 0
            while (result.size < need && attempts < 12) {
                for (word in dictDao.randomWords(600)) {
                    if (result.size >= need) break
                    if (used.add(word.id)) result.add(word)
                }
                attempts++
            }
        }
        return result
    }

    private suspend fun sampleGroup(target: Int, group: Int, used: MutableSet<Long>): List<WordEntity> {
        if (target <= 0) return emptyList()
        val dictDao = dictDb?.dictionaryDao() ?: return emptyList()
        val out = mutableListOf<WordEntity>()
        var attempts = 0
        while (out.size < target && attempts < 8) {
            for (word in dictDao.randomWordsInGroup(group, 200)) {
                if (out.size >= target) break
                if (used.add(word.id)) out.add(word)
            }
            attempts++
        }
        return out
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
                    status = if (isFree) STUDY_STATUS_FREE else STUDY_STATUS_LEARNING,
                    learnedDate = if (isFree) null else today,
                    nextReviewDate = if (isFree) null else plusDays(1),
                    ease = 2.5,
                    repetitions = 0,
                    lastInterval = 1,
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

    /**
     * Smart re-insertion for 稍后再看 (优化项三): the deferred word gets a cool-down by being
     * re-inserted at slot 4 of the remaining queue rather than bouncing straight back to the
     * front; short queues (fewer than 4 remaining) drop it to the end.
     */
    fun deferWord() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase != StudyPhase.LEARN && cur.phase != StudyPhase.FREE_PLAY) return
        val card = cur.card ?: return
        learnQueue.removeFirst()
        val idx = minOf(DEFER_INSERT_SLOT, learnQueue.size)
        learnQueue.add(idx, card)
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
            else dao.upsert(
                StudyWordEntity(
                    wordId = wordId,
                    status = STUDY_STATUS_MASTERED,
                    learnedDate = null,
                    nextReviewDate = null,
                    ease = 2.5,
                    repetitions = 0,
                    lastInterval = 0,
                    addedAt = System.currentTimeMillis(),
                    masteredAt = System.currentTimeMillis(),
                ),
            )
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

    private fun plusDays(days: Int): String = LocalDate.now().plusDays(days.toLong()).toString()

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