package com.chloemlla.cdict.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chloemlla.cdict.core.data.DatabaseState
import com.chloemlla.cdict.core.data.DictionaryDatabase
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.RecommendationEngine
import com.chloemlla.cdict.core.data.STUDY_STATUS_LEARNING
import com.chloemlla.cdict.core.data.STUDY_STATUS_MASTERED
import com.chloemlla.cdict.core.data.StudyDao
import com.chloemlla.cdict.core.data.StudyDatabase
import com.chloemlla.cdict.core.data.StudyWordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 与背词页共享同一份每日目标与筛选范围（同一 SharedPreferences 文件与键），两页对「今天学多少、
 * 从哪个词表哪个频率组里学」保持同一口径。
 */
private const val REC_PREFS_NAME = "cdict_study_settings"
private const val REC_PREF_KEY_GOAL = "study_daily_goal"
private const val REC_PREF_KEY_SCOPE_TAG = "study_scope_curriculum_tag"
private const val REC_PREF_KEY_SCOPE_GROUP = "study_scope_frequency_group"

/** “稍后再看”打散：插回到当前词之后第 4~6 位（PRD 智能插入）。 */
private const val DEFER_INSERT_SLOT = 5

/**
 * 阅读上下文只为队首和紧随其后的一张卡加载：队列一天最多 200 张，整队预取会变成数百次
 * Room 查询；预取到第二张是为了翻卡时不闪空。
 */
private const val CONTEXT_PREFETCH = 2
private const val CONTEXT_SENTENCE_LIMIT = 2

/**
 * 推荐页 ViewModel：持有内存中的推荐流队列（ArrayDeque），负责“纳入复习计划 / 已掌握 /
 * 稍后再看 / 改目标 / 再来一批”的实时队列重排，并把处理过的词实时落库 study.db（方案C 流转
 * 状态同步），使背词引擎的 ASR 状态机自动跳过已处理词。构造不触发任何网络；全部数据来自本地 Room。
 */
class RecommendationViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication<Application>()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(REC_PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<RecommendationScreenState>(RecommendationScreenState.Loading)
    val state: StateFlow<RecommendationScreenState> = _state.asStateFlow()

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())

    private var dictDb: DictionaryDatabase? = null
    private var studyDb: StudyDatabase? = null
    private val queue = ArrayDeque<RecommendationItemCard>()
    private val consumedToday = mutableSetOf<Long>()
    private var handledToday = 0

    // “稍后再看”会把同一张卡推回队首，缓存住已查过的上下文避免重复查库。
    private val contextCache = mutableMapOf<Long, RecommendationReadingContext>()

    private val today: String get() = LocalDate.now().toString()

    init {
        viewModelScope.launch {
            when (val result = DictionaryRepository(context).open()) {
                is DatabaseState.Ready -> {
                    dictDb = result.database
                    studyDb = StudyDatabase.open(context)
                    val dao = studyDb!!.studyDao()
                    _availableTags.value = result.database.dictionaryDao().distinctCurriculumTags()
                        .flatMap { it.split(",").map(String::trim).filter(String::isNotEmpty) }
                        .distinct()
                        .sorted()
                    // collect 永不返回，必须独立 launch，否则下面的首次建流不会执行。
                    viewModelScope.launch { observeMastered(dao) }
                    buildFeed()
                }
                is DatabaseState.Failed -> {
                    _state.value = RecommendationScreenState.NoDictionary
                }
                DatabaseState.Loading -> Unit
            }
        }
    }

    /**
     * 词典页的「已掌握」开关直接写 study.db，内存队列原先只能等切标签时的 [syncFromStore] 才对齐；
     * 这里靠 Room Flow 实时把已掌握的词从队列剔除并补足。
     */
    private suspend fun observeMastered(dao: StudyDao) {
        dao.masteredIds().collect { ids ->
            val mastered = ids.toSet()
            if (queue.none { it.word.id in mastered }) return@collect
            queue.retainAll { it.word.id !in mastered }
            topUpToQuota()
            emitAndHydrate()
        }
    }

    private fun dailyGoal(): Int =
        prefs.getInt(REC_PREF_KEY_GOAL, DAILY_GOAL_DEFAULT)
            .coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)

    private fun savedScope(): StudyScope = StudyScope(
        curriculumTag = prefs.getString(REC_PREF_KEY_SCOPE_TAG, null)?.takeIf(String::isNotEmpty),
        frequencyGroup = if (prefs.contains(REC_PREF_KEY_SCOPE_GROUP)) prefs.getInt(REC_PREF_KEY_SCOPE_GROUP, -1).let { if (it in 1..7) it else null } else null,
    )

    fun onScopeChange(scope: StudyScope) {
        prefs.edit {
            if (scope.curriculumTag != null) putString(REC_PREF_KEY_SCOPE_TAG, scope.curriculumTag)
            else remove(REC_PREF_KEY_SCOPE_TAG)
            if (scope.frequencyGroup != null) putInt(REC_PREF_KEY_SCOPE_GROUP, scope.frequencyGroup)
            else remove(REC_PREF_KEY_SCOPE_GROUP)
        }
        viewModelScope.launch { buildFeed() }
    }

    /** 依据当前每日目标从头重建整条推荐流（点刷新时调用）。 */
    fun reload() {
        viewModelScope.launch { buildFeed() }
    }

    /**
     * 回到推荐标签时的轻量同步：今日进度以 study.db 为准重算，剔除已在背词页处理过的词，
     * 再补足到今日剩余额度。不重建整条流，用户当前看到的卡片不会被换掉。
     */
    fun syncFromStore() {
        if (_state.value !is RecommendationScreenState.Ready) return
        viewModelScope.launch {
            val dao = studyDb?.studyDao() ?: return@launch
            val handled = dao.allStudiedIds().toSet()
            queue.retainAll { it.word.id !in handled }
            handledToday = dao.learnedTodayCount(today)
            topUpToQuota()
            emitAndHydrate()
        }
    }

    private suspend fun engine(): RecommendationEngine? {
        val dictDao = dictDb?.dictionaryDao() ?: return null
        val dao = studyDb?.studyDao() ?: return null
        return RecommendationEngine(dictDao, dao)
    }

    private suspend fun buildFeed() {
        val dao = studyDb?.studyDao() ?: return
        queue.clear()
        contextCache.clear()
        handledToday = dao.learnedTodayCount(today)
        topUpToQuota()
        emitAndHydrate()
    }

    /**
     * 先按当前队列刷新界面再补上下文：队首上下文通常已在上一轮预取过，翻卡不必等库；
     * 这一轮只会为新露出的第二张卡查询。
     */
    private suspend fun emitAndHydrate() {
        emit()
        hydrateHead()
        emit()
    }

    /** 只给队首 [CONTEXT_PREFETCH] 张卡加载例句 / 热图与学习状态，其余卡片的上下文留空。 */
    private suspend fun hydrateHead() {
        val dictDao = dictDb?.dictionaryDao() ?: return
        val studyDao = studyDb?.studyDao() ?: return
        for (index in 0 until minOf(CONTEXT_PREFETCH, queue.size)) {
            val card = queue[index]
            val id = card.word.id
            val reading = contextCache.getOrPut(id) {
                RecommendationReadingContext(
                    sentences = dictDao.sentences(id, CONTEXT_SENTENCE_LIMIT, 0)
                        .filter { it.english.isNotBlank() },
                    heatmap = dictDao.heatmap(id).filter { it.period.isNotBlank() },
                )
            }
            val studyState = recommendationStudyState(studyDao.word(id)?.status)
            if (card.reading != reading || card.studyState != studyState) {
                queue[index] = card.copy(reading = reading, studyState = studyState)
            }
        }
    }

    /**
     * 把队列补足到「今日剩余额度 = 每日目标 − 今日已完成」。只补不裁，这样「再来一批」额外
     * 生成的卡片不会在下一次同步时被削掉。
     */
    private suspend fun topUpToQuota() {
        val shortfall = (dailyGoal() - handledToday).coerceAtLeast(0) - queue.size
        if (shortfall <= 0) return
        val eng = engine() ?: return
        val scope = savedScope()
        val occupied = consumedToday + queue.map { it.word.id }
        eng.generateFeed(shortfall, today, occupied, scope.curriculumTag, scope.frequencyGroup)
            .items.forEach { (word, pool) -> queue.addLast(RecommendationItemCard(word, pool)) }
    }

    /**
     * “纳入复习计划”：把当前词写入 study.db 的复习管线（learning，明日首轮复习），供背词页的
     * 四选一 / ASR 状态机做提取练习；并从推荐流移除。它当天不会再出现在背词页的新词卡里——
     * 推荐页只负责“输入（预热）”，提取练习从明天的复习开始（方案C：先预热、再间隔测试）。
     */
    fun markLearned() {
        val cur = _state.value as? RecommendationScreenState.Ready ?: return
        val head = cur.items.firstOrNull() ?: return
        val id = head.word.id
        viewModelScope.launch {
            studyDb?.studyDao()?.upsert(
                StudyWordEntity(
                    wordId = id,
                    status = STUDY_STATUS_LEARNING,
                    learnedDate = today,
                    nextReviewDate = plusDays(1),
                    ease = 2.5,
                    repetitions = 0,
                    lastInterval = 1,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            consume(id)
        }
    }

    /**
     * “已掌握（直接消灭）”：立即写入 mastered 并实时同步到 study.db，背词引擎的 ASR 状态机会
     * 自动跳过该词（不再进入新词 / 复习池）；并从推荐流直接移除。已掌握不占用今日额度——
     * 它是把词从词库里划掉，而不是今天学了一个，因此队列会随即补进一张新卡。
     */
    fun markMastered() {
        val cur = _state.value as? RecommendationScreenState.Ready ?: return
        val head = cur.items.firstOrNull() ?: return
        val id = head.word.id
        viewModelScope.launch {
            studyDb?.studyDao()?.upsert(
                StudyWordEntity(
                    wordId = id,
                    status = STUDY_STATUS_MASTERED,
                    learnedDate = today,
                    addedAt = System.currentTimeMillis(),
                    masteredAt = System.currentTimeMillis(),
                ),
            )
            consume(id)
        }
    }

    /** 从队列移除并在本会话排除，今日进度以 study.db 为准重算后刷新 UI。 */
    private suspend fun consume(id: Long) {
        // The db upsert before this call is a suspend point, so the queue head may
        // have shifted (e.g. a concurrent defer); remove the exact item rather than
        // the current head to avoid dropping the wrong card.
        val idx = queue.indexOfFirst { it.word.id == id }
        if (idx < 0) return
        consumedToday.add(id)
        queue.removeAt(idx)
        handledToday = studyDb?.studyDao()?.learnedTodayCount(today) ?: handledToday
        topUpToQuota()
        emitAndHydrate()
    }

    /** 稍后再看：打散插回到当前位置之后第 [DEFER_INSERT_SLOT] 位（队尾不足则回尾）。 */
    fun defer() {
        val cur = _state.value as? RecommendationScreenState.Ready ?: return
        val head = cur.items.firstOrNull() ?: return
        queue.removeFirst()
        val insertAt = minOf(DEFER_INSERT_SLOT, queue.size)
        queue.add(insertAt, head)
        viewModelScope.launch { emitAndHydrate() }
    }

    /** 再来一批：词池学光后按目标再生成一批（终极复习 / 补充）。 */
    fun continueMore() {
        viewModelScope.launch {
            val eng = engine() ?: return@launch
            val goal = dailyGoal()
            val scope = savedScope()
            val occupied = consumedToday + queue.map { it.word.id }
            val feed = eng.generateFeed(goal, today, occupied, scope.curriculumTag, scope.frequencyGroup)
            feed.items.forEach { (word, pool) -> queue.addLast(RecommendationItemCard(word, pool)) }
            emitAndHydrate()
        }
    }

    /** 修改每日目标：调高则补足到新额度，调低则从队尾裁剪（PRD 无缝扩充）。 */
    fun setGoal(value: Int) {
        val goal = value.coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)
        val prev = dailyGoal()
        prefs.edit { putInt(REC_PREF_KEY_GOAL, goal) }
        if (_state.value !is RecommendationScreenState.Ready) return
        viewModelScope.launch {
            if (goal > prev) {
                topUpToQuota()
            } else if (goal < prev) {
                // Goal is the day's total target; already-handled words still count
                // toward it, so trim the queue to what remains to be shown.
                val remaining = (goal - handledToday).coerceAtLeast(0)
                repeat((queue.size - remaining).coerceAtLeast(0)) { queue.removeLast() }
            }
            emitAndHydrate()
        }
    }

    private fun plusDays(days: Int): String = LocalDate.now().plusDays(days.toLong()).toString()

    private fun emit() {
        _state.value = RecommendationScreenState.Ready(
            dailyGoal = dailyGoal(),
            items = queue.toList(),
            handledToday = handledToday,
            scope = savedScope(),
            availableCurriculumTags = _availableTags.value,
        )
    }
}

class RecommendationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RecommendationViewModel(context.applicationContext as Application) as T
    }
}