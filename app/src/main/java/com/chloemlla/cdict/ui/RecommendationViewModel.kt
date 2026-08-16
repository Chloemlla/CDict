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
import com.chloemlla.cdict.core.data.StudyDatabase
import com.chloemlla.cdict.core.data.StudyWordEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 与背词页共享同一个每日目标（同一 SharedPreferences），保证两侧进度一致。 */
private const val REC_PREFS_NAME = "cdict_study_settings"
private const val REC_PREF_KEY_GOAL = "study_daily_goal"

/** “稍后再看”打散：插回到当前词之后第 4~6 位（PRD 智能插入）。 */
private const val DEFER_INSERT_SLOT = 5

/**
 * 推荐页 ViewModel：持有内存中的推荐流队列（ArrayDeque），负责“加入今日背词任务 / 已掌握 /
 * 稍后再看 / 改目标 / 再来一批”的实时队列重排，并把处理过的词实时落库 study.db（方案C 流转
 * 状态同步），使背词引擎的 ASR 状态机自动跳过已处理词。构造不触发任何网络；全部数据来自本地 Room。
 */
class RecommendationViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication<Application>()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(REC_PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<RecommendationScreenState>(RecommendationScreenState.Loading)
    val state: StateFlow<RecommendationScreenState> = _state.asStateFlow()

    private var dictDb: DictionaryDatabase? = null
    private var studyDb: StudyDatabase? = null
    private val queue = ArrayDeque<RecommendationItemCard>()
    private val consumedToday = mutableSetOf<Long>()
    private var totalCount = 0
    private var handledToday = 0

    private val today: String get() = LocalDate.now().toString()

    init {
        viewModelScope.launch {
            when (val result = DictionaryRepository(context).open()) {
                is DatabaseState.Ready -> {
                    dictDb = result.database
                    studyDb = StudyDatabase.open(context)
                    buildFeed()
                }
                is DatabaseState.Failed -> {
                    _state.value = RecommendationScreenState.NoDictionary
                }
                DatabaseState.Loading -> Unit
            }
        }
    }

    private fun dailyGoal(): Int =
        prefs.getInt(REC_PREF_KEY_GOAL, DAILY_GOAL_DEFAULT)
            .coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)

    /** 依据当前每日目标从头重建整条推荐流（点刷新时调用）。 */
    fun reload() {
        viewModelScope.launch { buildFeed() }
    }

    private suspend fun engine(): RecommendationEngine? {
        val dictDao = dictDb?.dictionaryDao() ?: return null
        val dao = studyDb?.studyDao() ?: return null
        return RecommendationEngine(dictDao, dao)
    }

    private suspend fun buildFeed() {
        val eng = engine() ?: return
        val goal = dailyGoal()
        val occupied = consumedToday + queue.map { it.word.id }
        queue.clear()
        val feed = eng.generateFeed(goal, today, occupied)
        feed.items.forEach { (word, pool) -> queue.addLast(RecommendationItemCard(word, pool)) }
        totalCount = feed.items.size
        handledToday = 0
        emit()
    }

    /**
     * “加入今日背词任务”：把当前词写入 study.db 的复习管线（learning，明日首轮复习），供背词页的
     * 四选一 / ASR 状态机做提取练习；并从推荐流移除。推荐页只负责“输入（预热）”，真正的测验
     * 交给背词页（方案C 统一数据源：同一 StudyStatus 实时可见）。
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
     * 自动跳过该词（不再进入新词 / 复习池）；并从推荐流直接移除。
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

    /** 从队列移除并在本会话排除，推进今日已处理计数后刷新 UI。 */
    private fun consume(id: Long) {
        consumedToday.add(id)
        handledToday++
        queue.removeFirst()
        emit()
    }

    /** 稍后再看：打散插回到当前位置之后第 [DEFER_INSERT_SLOT] 位（队尾不足则回尾）。 */
    fun defer() {
        val cur = _state.value as? RecommendationScreenState.Ready ?: return
        val head = cur.items.firstOrNull() ?: return
        queue.removeFirst()
        val insertAt = minOf(DEFER_INSERT_SLOT, queue.size)
        queue.add(insertAt, head)
        emit()
    }

    /** 再来一批：词池学光后按目标再生成一批（终极复习 / 补充）。 */
    fun continueMore() {
        viewModelScope.launch {
            val eng = engine() ?: return@launch
            val goal = dailyGoal()
            val occupied = consumedToday + queue.map { it.word.id }
            val feed = eng.generateFeed(goal, today, occupied)
            feed.items.forEach { (word, pool) -> queue.addLast(RecommendationItemCard(word, pool)) }
            totalCount += feed.items.size
            emit()
        }
    }

    /** 修改每日目标：追加符合 3:5:2 的新切片；降低则从队尾裁剪（PRD 无缝扩充）。 */
    fun setGoal(value: Int) {
        val goal = value.coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)
        val prev = dailyGoal()
        prefs.edit { putInt(REC_PREF_KEY_GOAL, goal) }
        val cur = _state.value as? RecommendationScreenState.Ready ?: return
        viewModelScope.launch {
            val delta = goal - prev
            when {
                delta > 0 -> {
                    val eng = engine() ?: return@launch
                    val occupied = consumedToday + queue.map { it.word.id }
                    val feed = eng.generateFeed(delta, today, occupied)
                    feed.items.forEach { (word, pool) -> queue.addLast(RecommendationItemCard(word, pool)) }
                    totalCount += feed.items.size
                }
                delta < 0 -> {
                    val remove = (queue.size - goal).coerceAtLeast(0)
                    repeat(remove) { queue.removeLast() }
                    totalCount = (totalCount - remove).coerceAtLeast(0)
                }
            }
            emit()
        }
    }

    private fun plusDays(days: Int): String = LocalDate.now().plusDays(days.toLong()).toString()

    private fun emit() {
        _state.value = RecommendationScreenState.Ready(
            dailyGoal = dailyGoal(),
            totalCount = totalCount,
            items = queue.toList(),
            handledToday = handledToday,
        )
    }
}

class RecommendationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RecommendationViewModel(context.applicationContext as Application) as T
    }
}