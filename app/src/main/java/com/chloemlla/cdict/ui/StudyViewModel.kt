package com.chloemlla.cdict.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.data.DatabaseState
import com.chloemlla.cdict.core.data.DictionaryDatabase
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.RecommendationEngine
import com.chloemlla.cdict.core.data.STUDY_STATUS_LEARNING
import com.chloemlla.cdict.core.data.STUDY_STATUS_MASTERED
import com.chloemlla.cdict.core.data.STUDY_STATUS_REVIEW
import com.chloemlla.cdict.core.data.StudyDao
import com.chloemlla.cdict.core.data.StudyDatabase
import com.chloemlla.cdict.core.data.StudyWordEntity
import com.chloemlla.cdict.core.data.WordEntity
import com.chloemlla.cdict.ui.about.AboutStore
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
private const val EASE_MIN = 1.3
private const val EASE_DEFAULT = 2.5

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

// Smart-cool-down insertion slot for 稍后再看 (优化项三).
private const val DEFER_INSERT_SLOT = 4

// 自由刷词的队列水位：低于阈值才一次性补满。每刷完一张就补 1 个会让推荐引擎（含两次
// ORDER BY RANDOM() 全表扫描）在每一次翻卡时都跑一遍。
private const val FREE_PLAY_QUEUE_TARGET = 12
private const val FREE_PLAY_REFILL_AT = 4

private const val PREF_KEY_GOAL = "study_daily_goal"
private const val PREFS_NAME = "cdict_study_settings"
private const val PREF_KEY_SCOPE_TAG = "study_scope_curriculum_tag"
private const val PREF_KEY_SCOPE_GROUP = "study_scope_frequency_group"

class StudyViewModel(private val context: Context) : ViewModel() {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val aboutStore = AboutStore(context)

    private val _state = MutableStateFlow<StudyScreenState>(StudyScreenState.Loading)
    val state: StateFlow<StudyScreenState> = _state.asStateFlow()

    private val _masteredIds = MutableStateFlow<Set<Long>>(emptySet())
    val masteredIds: StateFlow<Set<Long>> = _masteredIds.asStateFlow()

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags.asStateFlow()

    private var dictDb: DictionaryDatabase? = null
    private var studyDb: StudyDatabase? = null

    // 词典单例的世代号。「重建词库」会关掉旧实例，缓存下来的 dao 全部作废。
    private var dictGeneration: Int = DictionaryRepository.generation.value

    // 新词投喂与推荐页共用一台引擎；它无状态，每次抽词新建一台只是白跑一遍构造。
    private var recommender: RecommendationEngine? = null

    private val reviewQueue = ArrayDeque<ReviewQuestion>()
    private val learnQueue = ArrayDeque<WordEntity>()

    // 当堂检测中的那张新词卡与题目。答对前它仍留在 learnQueue 里，放弃作答即退回队列。
    private var quizCard: WordEntity? = null
    private var quizQuestion: ReviewQuestion? = null

    // 干扰项词池按会话缓存：每张新词卡都重跑一次 randomWords(400) 会拖慢翻卡。
    private var cachedDistractorPool: List<WordEntity>? = null

    // 自由刷词不落库，只在本次会话内去重，避免同一张卡反复出现。
    private val freePlayed = mutableSetOf<Long>()

    private var learnedToday = mutableListOf<WordEntity>()
    private var reviewTotal = 0

    // 今日进度只有 study.db 一份真相；内存里再记一个计数就必然会和清单对不上。
    private val todayDone: Int get() = learnedToday.size

    // 「我已背会」的查库与组题在途：期间不接受第二次提交，也不接受把这张卡推迟掉。
    private var committing = false

    // Monotonic clock (not wall clock) for when the current review question became answerable,
    // and the hesitation it was finally answered with; feeds the Error-Attribution engine.
    private var questionShownAt = 0L
    private var lastAnswerElapsed = 0L

    // True while running an on-demand immediate test of today's newly-learned words, so the
    // review header labels it as such rather than as "昨日复习". Reset when the review exits.
    private var isImmediateTest = false

    private val today: String get() = LocalDate.now().toString()

    // 会话开始时的日期。跨过零点后 today 会变，而队列与今日清单仍是旧日期的，必须整体重载。
    private var sessionDate: String = LocalDate.now().toString()

    init {
        viewModelScope.launch {
            val dao = openDictionary() ?: return@launch
            viewModelScope.launch {
                dao.masteredIds().collect { _masteredIds.value = it.toSet() }
            }
            runCatching { reloadNow() }.onFailure {
                _state.value = StudyScreenState.NoDictionary("学习进度读取失败：${failureText(it)}")
            }
        }
    }

    /**
     * 打开词典库与学习库，并重建一切持有 dao 的对象。返回 null 表示词库不可用，此时状态已落成
     * [StudyScreenState.NoDictionary]。单例的开关只归 [DictionaryRepository] 管，这里不自行 close。
     */
    private suspend fun openDictionary(): StudyDao? {
        val result = DictionaryRepository(context).open()
        if (result is DatabaseState.Failed) {
            _state.value = StudyScreenState.NoDictionary(result.message)
            return null
        }
        val database = (result as? DatabaseState.Ready)?.database ?: return null
        return runCatching {
            dictDb = database
            val dictDao = database.dictionaryDao()
            val studyDatabase = studyDb ?: StudyDatabase.open(context)
            studyDb = studyDatabase
            val dao = studyDatabase.studyDao()
            // 引擎持有 dictDao，不一起重建就会继续打在已关闭的旧库上。
            recommender = RecommendationEngine(dictDao, dao)
            _availableTags.value = dictDao.distinctCurriculumTags()
                .flatMap { it.split(",").map(String::trim).filter(String::isNotEmpty) }
                .distinct()
                .sorted()
            dictGeneration = DictionaryRepository.generation.value
            dao
        }.getOrElse {
            _state.value = StudyScreenState.NoDictionary("学习数据打开失败：${failureText(it)}")
            null
        }
    }

    private fun failureText(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName

    private fun dailyGoal(): Int = prefs.getInt(PREF_KEY_GOAL, DAILY_GOAL_DEFAULT).coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)

    private fun savedScope(): StudyScope = StudyScope(
        curriculumTag = prefs.getString(PREF_KEY_SCOPE_TAG, null)?.takeIf(String::isNotEmpty),
        frequencyGroup = if (prefs.contains(PREF_KEY_SCOPE_GROUP)) prefs.getInt(PREF_KEY_SCOPE_GROUP, -1).let { if (it in 1..7) it else null } else null,
    )

    fun onScopeChange(scope: StudyScope) {
        prefs.edit {
            if (scope.curriculumTag != null) putString(PREF_KEY_SCOPE_TAG, scope.curriculumTag)
            else remove(PREF_KEY_SCOPE_TAG)
            if (scope.frequencyGroup != null) putInt(PREF_KEY_SCOPE_GROUP, scope.frequencyGroup)
            else remove(PREF_KEY_SCOPE_GROUP)
        }
        reload()
    }

    fun setGoal(value: Int) {
        val goal = value.coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)
        prefs.edit { putInt(PREF_KEY_GOAL, goal) }
        viewModelScope.launch {
            runCatching {
                val cur = _state.value as? StudyScreenState.Ready ?: return@runCatching
                when {
                    cur.phase == StudyPhase.LEARN || cur.phase == StudyPhase.FREE_PLAY -> {
                        topUpLearnQueue(isFree = cur.phase == StudyPhase.FREE_PLAY)
                        emit(cur.phase, card = learnQueue.firstOrNull())
                    }
                    // 总结页的目标步进器是常驻控件：调高后若还停在总结态，进度条会显示 20/30
                    // 而同屏文案仍写「今日学习已达标」，且页面上没有任何继续背词的入口。
                    cur.phase == StudyPhase.DONE && todayDone < goal -> {
                        topUpLearnQueue(isFree = false)
                        emit(StudyPhase.LEARN, card = learnQueue.firstOrNull())
                    }
                    else -> emit(cur.phase, question = cur.question, card = cur.card)
                }
            }.onFailure { notifyUser("调整每日目标失败：${failureText(it)}") }
        }
    }

    /** Resets the whole in-memory session from persisted state, e.g. on entering the tab. */
    fun reload() {
        viewModelScope.launch {
            runCatching { reloadNow() }.onFailure { notifyUser("刷新背词计划失败：${failureText(it)}") }
        }
    }

    private suspend fun reloadNow() {
        // 「重建词库」关掉了旧的词典单例，缓存的 dictDb 与 recommender 会一路抛 SQLiteException，
        // 背词页此后既拿不到新词也组不出复习题，必须先整套重取。
        if (DictionaryRepository.generation.value != dictGeneration) {
            openDictionary() ?: return
        }
        val dao = studyDb?.studyDao() ?: return
        isImmediateTest = false
        sessionDate = today
        learnQueue.clear()
        reviewQueue.clear()
        quizCard = null
        quizQuestion = null
        refreshLearnedToday()
        val pending = dao.pendingReview(today, reviewCap())
        if (pending.isNotEmpty()) {
            buildReview(pending)
        } else if (todayDone >= dailyGoal()) {
            emit(StudyPhase.DONE)
        } else {
            buildLearn()
        }
    }

    private fun reviewCap(): Int = (dailyGoal() * REVIEW_CAP_MULTIPLIER).toInt()
        .coerceAtMost(REVIEW_ABSENCE_HARD_CAP)
        .coerceAtLeast(DAILY_GOAL_MIN)

    /**
     * 回到背词标签时的轻量同步：今日进度以 study.db 为准重算，并把已在推荐页处理过的词从
     * 内存学习队列里剔除，避免同一个词在两页各出现一次。复习与当堂检测进行中（含答题反馈）
     * 不打扰，只在学习 / 完成态刷新，防止把用户正在答的题重置掉。
     */
    fun syncFromStore() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase == StudyPhase.REVIEW || cur.phase == StudyPhase.LEARN_QUIZ) return
        viewModelScope.launch {
            runCatching {
                // 挂在后台过夜后切回来：队列与今日清单都还是昨天的，只重算计数会直接开始发新词。
                if (today != sessionDate) {
                    reloadNow()
                    return@runCatching
                }
                val dao = studyDb?.studyDao() ?: return@runCatching
                val handled = dao.allStudiedIds().toSet()
                learnQueue.retainAll { it.id !in handled }
                refreshLearnedToday()
                val isFree = cur.phase == StudyPhase.FREE_PLAY
                // 到期复习优先于新词。自由刷词是用户主动选的旁路，不在那里打断他。
                val pending = if (isFree) emptyList() else dao.pendingReview(today, reviewCap())
                if (pending.isNotEmpty()) {
                    buildReview(pending)
                    return@runCatching
                }
                if (!isFree && todayDone >= dailyGoal()) {
                    emit(StudyPhase.DONE)
                    return@runCatching
                }
                topUpLearnQueue(isFree)
                emit(if (isFree) StudyPhase.FREE_PLAY else StudyPhase.LEARN, card = learnQueue.firstOrNull())
            }.onFailure { notifyUser("同步学习进度失败：${failureText(it)}") }
        }
    }

    // ---- Review phase -----------------------------------------------------------------

    private suspend fun buildReview(pending: List<StudyWordEntity>) {
        val dictDao = dictDb?.dictionaryDao() ?: return
        val dao = studyDb?.studyDao() ?: return
        reviewQueue.clear()
        reviewTotal = 0
        val byId = dictDao.wordsByIds(pending.map { it.wordId }).associateBy { it.id }
        val pool = distractorPool()
        // Prioritise core IELTS words first (PRD §3.3): the review batch is drawn from the
        // decayed pool but re-ordered by frequencyGroup so high-frequency words surface
        // before obscure ones when the backlog exceeds a single session.
        val ordered = pending.sortedBy { byId[it.wordId]?.frequencyGroup ?: Int.MAX_VALUE }
        for (entry in ordered) {
            val question = byId[entry.wordId]?.let { buildQuestion(it, pool) }
            if (question == null) {
                // 出不了题的词若原地留在队列里，它的到期日永远不前进，而 pendingReview 按到期日
                // 排序，于是它每天都优先占住复习窗口的名额，把真正的到期词全部饿死。
                dao.postpone(entry.wordId, plusDays(1))
                continue
            }
            reviewQueue.addLast(question)
        }
        reviewTotal = reviewQueue.size
        if (reviewQueue.isEmpty()) startLearningPhase() else emit(StudyPhase.REVIEW, question = reviewQueue.firstOrNull())
    }

    /**
     * 四选一题目：正确答案允许由 translation 回退到 definition，凑不满三个干扰项则出不了题。
     * 复习、当堂检测、立即测试与开发者面板共用这一处组题，题目里带上 frequencyGroup，
     * 排程写入就不必为每道答对的题再回 dict.db 查一次整行。
     */
    private fun buildQuestion(word: WordEntity, pool: List<WordEntity>): ReviewQuestion? {
        val correctText = word.translation?.takeIf(String::isNotBlank)
            ?: word.definition?.takeIf(String::isNotBlank) ?: return null
        val distractors = buildReviewDistractors(word, pool)
        if (distractors.size < 3) return null
        val options = (listOf(correctText) + distractors).shuffled()
        return ReviewQuestion(
            wordId = word.id,
            english = word.word,
            phonetic = phoneticsOf(word),
            options = options,
            correctIndex = options.indexOf(correctText),
            frequencyGroup = word.frequencyGroup,
        )
    }

    fun answerReview(index: Int) {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        val question = cur.question ?: return
        if (cur.phase != StudyPhase.REVIEW && cur.phase != StudyPhase.LEARN_QUIZ) return
        // 错因归因要的是「看到题目到作答」的时长。重试题改由 advanceAfterFeedback 换上，
        // 到那时已经混进了读错误提示的时间，所以在这里就把它定下来。
        lastAnswerElapsed = SystemClock.elapsedRealtime() - questionShownAt
        val chosen = question.options.getOrNull(index)
        val correct = index == question.correctIndex
        // 答错时不替换题目：question.attempt 一变，页面就会滚回顶部、犹豫计时被重置，
        // 而错误横幅和「继续」按钮都在选项下方，小屏上用户看不到自己错在哪。
        emit(
            cur.phase,
            question = question,
            card = cur.card,
            feedback = ReviewFeedback(correct, question.correctText, chosen),
        )
    }

    /**
     * Error-Attribution Engine (优化项五): classify the miss by hesitation time and tailor the
     * retry. A fast wrong answer means orthographic/blind confusion (the same option set is
     * pinned for a focused re-discrimination); a slow wrong answer signals an unfamiliar word,
     * so the retry re-shows the 释义 card first.
     */
    private fun retryPlanFor(question: ReviewQuestion): ReviewQuestion = question.copy(
        attempt = question.attempt + 1,
        confusionRetry = lastAnswerElapsed < CONFUSION_MAX_MS,
        forceReveal = lastAnswerElapsed > UNKNOWN_MIN_MS,
    )

    /**
     * Progresses past the displayed feedback. In the review phase a correct answer promotes the
     * word up the adaptive spacing ladder and a wrong answer knocks it back a rung and requeues
     * the (attribution-tailored) retry to the end; in the 当堂检测 phase a correct answer finally
     * commits the new word and a wrong answer re-presents the same question.
     */
    fun advanceAfterFeedback() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        val feedback = cur.feedback ?: return
        when (cur.phase) {
            StudyPhase.REVIEW -> {
                val question = reviewQueue.firstOrNull() ?: return
                reviewQueue.removeFirst()
                if (feedback.correct) {
                    viewModelScope.launch {
                        runCatching { scheduleReview(question) }
                            .onFailure { notifyUser("本题进度未保存：${failureText(it)}") }
                    }
                    if (reviewQueue.isEmpty()) {
                        aboutStore.reviewRoundDone = true
                        viewModelScope.launch {
                            runCatching { startLearningPhase() }
                                .onFailure { notifyUser("进入学习失败：${failureText(it)}") }
                        }
                    } else emit(StudyPhase.REVIEW, question = reviewQueue.first())
                } else {
                    reviewQueue.addLast(retryPlanFor(question))
                    viewModelScope.launch {
                        runCatching { recordLapse(question.wordId) }
                            .onFailure { notifyUser("本题进度未保存：${failureText(it)}") }
                    }
                    emit(StudyPhase.REVIEW, question = reviewQueue.first())
                }
            }
            StudyPhase.LEARN_QUIZ -> {
                val card = quizCard ?: return
                val question = quizQuestion ?: return
                if (feedback.correct) {
                    quizCard = null
                    quizQuestion = null
                    viewModelScope.launch {
                        runCatching { commitLearned(card, isFree = false) }
                            .onFailure { notifyUser("进度未保存：${failureText(it)}") }
                    }
                } else {
                    val retry = retryPlanFor(question)
                    quizQuestion = retry
                    emit(StudyPhase.LEARN_QUIZ, question = retry, card = card)
                }
            }
            else -> return
        }
    }

    /**
     * Adaptive Spaced Repetition write: advance the memory state on a correct review.
     * The base 1/3/7/15/30-day ladder is bent by the word's IELTS frequency band (PRD §3.3)
     * so core words are re-tested sooner and obscure words spread further apart.
     */
    private suspend fun scheduleReview(question: ReviewQuestion) {
        val dao = studyDb?.studyDao() ?: return
        val row = dao.word(question.wordId) ?: return
        // 同日幂等：「立即测试今日所学」可以无限重复，同一天再答对不能把阶梯再推一格，
        // 否则连做 5 轮就把今天刚背的词全判成已掌握、此后再也不复习。
        if (row.lastReviewedDate == today) return
        val slot = row.repetitions.coerceIn(0, REVIEW_INTERVALS.lastIndex)
        val ease = (row.ease + EASE_DELTA).coerceIn(EASE_MIN, EASE_MAX)
        val interval = decayedInterval(REVIEW_INTERVALS[slot], question.frequencyGroup, ease)
        val repetitions = row.repetitions + 1
        val status = if (repetitions >= MASTER_REPETITIONS) STUDY_STATUS_MASTERED else STUDY_STATUS_REVIEW
        dao.schedule(question.wordId, status, plusDays(interval), ease, repetitions, interval, today)
    }

    /**
     * 答错的写入。此前答错完全不落库，于是连续答错 5 天的词照样每天前进一格、第 5 天被判为
     * 已掌握。这里把阶梯打回首格、下调 ease 并累计失败次数，明天重来。
     */
    private suspend fun recordLapse(wordId: Long) {
        val dao = studyDb?.studyDao() ?: return
        val row = dao.word(wordId) ?: return
        dao.lapse(
            wordId = wordId,
            status = STUDY_STATUS_LEARNING,
            nextReviewDate = plusDays(1),
            ease = (row.ease - EASE_DELTA).coerceIn(EASE_MIN, EASE_MAX),
            lapses = row.lapses + 1,
        )
    }

    /**
     * Linear interpolation of the decay multiplier across the 7 frequency bands, scaled by the
     * word's own [ease] so repeatedly failed words really do come back sooner.
     */
    private fun decayedInterval(baseDays: Int, frequencyGroup: Int, ease: Double): Int {
        val t = (frequencyGroup.coerceIn(1, 7) - 1) / 6.0 // 0.0 (core) .. 1.0 (obscure)
        val mult = GROUP_DECAY_MULT_MIN + t * (GROUP_DECAY_MULT_MAX - GROUP_DECAY_MULT_MIN)
        return maxOf(1, (baseDays * mult * (ease / EASE_DEFAULT)).toInt())
    }

    /** Marks the current question as just presented, restarting the hesitation clock. */
    fun noteQuestionPresented() {
        questionShownAt = SystemClock.elapsedRealtime()
    }

    /**
     * Developer backdoor: launches the next-day review exam with a randomly sampled word so
     * the review UI can be exercised without a real due queue. Invisible to normal use
     * (only reachable via the developer panel in the study top bar, debug builds only).
     */
    fun debugLaunchReview() {
        viewModelScope.launch {
            val pool = distractorPool()
            val question = pool.firstNotNullOfOrNull { buildQuestion(it, pool) } ?: return@launch
            reviewQueue.clear()
            reviewTotal = 1
            reviewQueue.addLast(question)
            emit(StudyPhase.REVIEW, question = question)
        }
    }

    /**
     * On-demand immediate test of today's newly-learned words (无需等到明日): runs the words
     * just marked 我已背会 through the same review engine instead of making the learner wait
     * for tomorrow's scheduled first review. Answering a word correctly advances its adaptive
     * spacing schedule exactly as an on-time review would — the early call simply pulls the
     * ladder forward, it never discards a pending review.
     */
    fun startImmediateTest() {
        viewModelScope.launch {
            runCatching {
                val dao = studyDb?.studyDao() ?: return@runCatching
                refreshLearnedToday()
                // 今天已经排程过的词再测一遍只会重复推进阶梯，测满 5 轮就全变成已掌握。
                val alreadyReviewed = dao.reviewedTodayIds(today).toSet()
                val targets = learnedToday.filter { it.id !in alreadyReviewed }
                if (targets.isEmpty()) {
                    notifyUser("今日所学都已测过一轮，明天会安排正式复习")
                    return@runCatching
                }
                val pool = distractorPool()
                val questions = targets.mapNotNull { buildQuestion(it, pool) }
                // Nothing testable (no word could assemble a full option set): stay put rather
                // than stranding the UI on a ghost "preparing" review screen.
                if (questions.isEmpty()) {
                    notifyUser("这些词暂时凑不出四个选项，无法测试")
                    return@runCatching
                }
                reviewQueue.clear()
                reviewQueue.addAll(questions)
                reviewTotal = reviewQueue.size
                isImmediateTest = true
                emit(StudyPhase.REVIEW, question = reviewQueue.first())
            }.onFailure { notifyUser("无法开始测试：${failureText(it)}") }
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
        if (isFree && learnQueue.size > FREE_PLAY_REFILL_AT) return
        val target = if (isFree) FREE_PLAY_QUEUE_TARGET else (dailyGoal() - todayDone).coerceAtLeast(0)
        val need = (target - learnQueue.size).coerceAtLeast(0)
        if (need <= 0) return
        val occupied = learnQueue.mapTo(mutableSetOf()) { it.id }
        occupied.addAll(dao.allStudiedIds())
        occupied.addAll(freePlayed)
        sampleNewWords(need, occupied).forEach { learnQueue.addLast(it) }
    }

    /**
     * 新词投喂与推荐页共用同一台 [RecommendationEngine]：配比（核心 5 / 派生拓展 3 / 高频过渡 2）、
     * 课程标签与频率组筛选、冷启动兜底全部由引擎决定，两页不再各跑一套梯度算法。
     */
    private suspend fun sampleNewWords(need: Int, occupied: Set<Long>): List<WordEntity> {
        if (need <= 0) return emptyList()
        val engine = recommender ?: return emptyList()
        val scope = savedScope()
        return engine.generateFeed(need, today, occupied, scope.curriculumTag, scope.frequencyGroup)
            .items
            .map { it.word }
    }

    /**
     * 「我已背会」只是当堂检测的入口而非自我声明：先做一次提取练习，答对才由
     * [commitLearned] 入库与计入进度。自由刷词不计进度，测验无意义；已在推荐页入库的词已经
     * 在复习管线里，当堂再测一遍是重复劳动，两种情况都直接走 [commitLearned]。
     */
    fun markLearned() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        val card = cur.card ?: return
        if (cur.phase != StudyPhase.LEARN && cur.phase != StudyPhase.FREE_PLAY) return
        // 组题前要查库、首次还要抽 400 词干扰项池，几百毫秒里两个按钮原本都还能点：
        // 不挡住的话「稍后再看」会先把这张卡挪走，随后弹出的却仍是它的检测题。
        if (committing) return
        val isFree = cur.phase == StudyPhase.FREE_PLAY
        setBusy(true)
        viewModelScope.launch {
            try {
                runCatching {
                    val dao = studyDb?.studyDao() ?: return@runCatching
                    if (!isFree && dao.word(card.id) == null) {
                        val question = buildQuestion(card, distractorPool())
                        // 凑不出完整选项集时退回旧行为，别把用户留在残缺的选项列表前。
                        if (question != null) {
                            quizCard = card
                            quizQuestion = question
                            emit(StudyPhase.LEARN_QUIZ, question = question, card = card)
                            return@runCatching
                        }
                    }
                    commitLearned(card, isFree)
                }.onFailure { notifyUser("进度未保存：${failureText(it)}") }
            } finally {
                setBusy(false)
            }
        }
    }

    private suspend fun distractorPool(): List<WordEntity> {
        cachedDistractorPool?.let { return it }
        val pool = dictDb?.dictionaryDao()?.randomWords(400).orEmpty()
        cachedDistractorPool = pool
        return pool
    }

    /**
     * 学会一个新词的落库与记账。写入的仍是首轮状态（repetitions 0 / 明日复习），当堂检测只是
     * 入门门槛，不能替代明日首轮复习去推进 ASR 阶梯。
     */
    private suspend fun commitLearned(card: WordEntity, isFree: Boolean) {
        // 自由刷词不落库。写一行 status='free' 会让这个词此后既不复习、也永远不再作为新词
        // 出现在任何一页（两处投喂都按 allStudiedIds 排除），与「不计入今日进度与明日复习」
        // 的文案完全相反，而且没有任何撤销入口。本次会话内去重即可。
        if (isFree) {
            freePlayed.add(card.id)
            learnQueue.remove(card)
            topUpLearnQueue(isFree = true)
            emit(StudyPhase.FREE_PLAY, card = learnQueue.firstOrNull())
            return
        }
        val dao = studyDb?.studyDao() ?: return
        // 该词可能已在推荐页「纳入复习计划」时入库。REPLACE 写入会把排好的 ASR 进度
        // 重置回首轮，所以已有记录时只把卡片摘出队列，不覆盖既有状态。
        if (dao.word(card.id) == null) {
            dao.upsert(
                StudyWordEntity(
                    wordId = card.id,
                    status = STUDY_STATUS_LEARNING,
                    learnedDate = today,
                    nextReviewDate = plusDays(1),
                    ease = EASE_DEFAULT,
                    repetitions = 0,
                    lastInterval = 1,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
        learnQueue.remove(card)
        refreshLearnedToday()
        topUpLearnQueue(isFree = false)
        if (todayDone >= dailyGoal()) emit(StudyPhase.DONE)
        else emit(StudyPhase.LEARN, card = learnQueue.firstOrNull())
    }

    /**
     * Smart re-insertion for 稍后再看 (优化项三): the deferred word gets a cool-down by being
     * re-inserted at slot 4 of the remaining queue rather than bouncing straight back to the
     * front; short queues (fewer than 4 remaining) drop it to the end. 当堂检测里它同时是退出口：
     * 实在不会的词不该把人永久卡在同一道题上，放弃作答即退回队列，不入库、不计进度。
     * 纯复习态下同一个按钮是「本轮先到这里」，见 [pauseReview]。
     */
    fun deferWord() {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.phase == StudyPhase.REVIEW) {
            pauseReview()
            return
        }
        val quitQuiz = cur.phase == StudyPhase.LEARN_QUIZ
        if (!quitQuiz && cur.phase != StudyPhase.LEARN && cur.phase != StudyPhase.FREE_PLAY) return
        if (committing) return
        val card = cur.card ?: return
        // 队列里只剩这一张卡时原地放回，emit 出的状态与上一帧逐字段相等，会被 StateFlow 去重，
        // 用户点了按钮却什么都没发生。
        if (!quitQuiz && learnQueue.size <= 1) {
            notifyUser("这是本轮最后一个词了")
            return
        }
        if (quitQuiz) {
            quizCard = null
            quizQuestion = null
        }
        learnQueue.remove(card)
        val idx = minOf(DEFER_INSERT_SLOT, learnQueue.size)
        learnQueue.add(idx, card)
        emit(if (quitQuiz) StudyPhase.LEARN else cur.phase, card = learnQueue.firstOrNull())
    }

    /**
     * 复习态的出口。此前答错只会把题挪到队尾、队列长度永不下降，断刷一周后回来的 50 题积压
     * 必须全部答对才能看到别的页面。清空本轮队列即可，不写库——这些词仍然到期，明天照旧排队。
     */
    private fun pauseReview() {
        viewModelScope.launch {
            runCatching {
                reviewQueue.clear()
                reviewTotal = 0
                startLearningPhase()
            }.onFailure { notifyUser("返回学习失败：${failureText(it)}") }
        }
    }

    fun continueFreePlay() {
        viewModelScope.launch {
            runCatching {
                topUpLearnQueue(isFree = true)
                emit(StudyPhase.FREE_PLAY, card = learnQueue.firstOrNull())
            }.onFailure { notifyUser("无法进入自由刷词：${failureText(it)}") }
        }
    }

    fun exitFreePlay() {
        emit(StudyPhase.DONE)
    }

    // ---- Shared / dictionary integration --------------------------------------------------

    fun toggleMastered(wordId: Long) {
        viewModelScope.launch {
            runCatching {
                val dao = studyDb?.studyDao() ?: return@runCatching
                when {
                    // 取消掌握绝不能删行：整行删掉会连学习日期与复习次数一起丢掉，该词还会作为
                    // 全新词重新进入投喂池，且没有任何撤销入口。
                    wordId in _masteredIds.value -> dao.unmarkMastered(wordId, today)
                    // 已有行只改状态：REPLACE 是整行替换，会把攒下的复习阶梯清零。
                    dao.word(wordId) != null -> dao.markMastered(wordId, System.currentTimeMillis())
                    else -> dao.upsert(
                        StudyWordEntity(
                            wordId = wordId,
                            status = STUDY_STATUS_MASTERED,
                            learnedDate = null,
                            nextReviewDate = null,
                            ease = EASE_DEFAULT,
                            repetitions = 0,
                            lastInterval = 0,
                            addedAt = System.currentTimeMillis(),
                            masteredAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }.onFailure { notifyUser("已掌握状态未保存：${failureText(it)}") }
        }
    }

    // ---- State helpers ---------------------------------------------------------------

    private suspend fun startLearningPhase() {
        // Leaving the review phase (back to learn/done) resets the immediate-test label.
        isImmediateTest = false
        refreshLearnedToday()
        if (todayDone >= dailyGoal()) emit(StudyPhase.DONE)
        else buildLearn()
    }

    private suspend fun refreshLearnedToday() {
        val ids = studyDb?.studyDao()?.learnedTodayIds(today).orEmpty()
        learnedToday = if (ids.isEmpty()) mutableListOf()
        else dictDb?.dictionaryDao()?.wordsByIds(ids).orEmpty().toMutableList()
    }

    /** 一次性提示：贴在当前状态上，下一次 [emit] 自然清掉。 */
    private fun notifyUser(message: String) {
        val cur = _state.value as? StudyScreenState.Ready ?: return
        _state.value = cur.copy(notice = message)
    }

    private fun setBusy(value: Boolean) {
        committing = value
        val cur = _state.value as? StudyScreenState.Ready ?: return
        if (cur.busy != value) _state.value = cur.copy(busy = value)
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
            isImmediateTest = isImmediateTest,
            scope = savedScope(),
            availableCurriculumTags = _availableTags.value,
            busy = committing,
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