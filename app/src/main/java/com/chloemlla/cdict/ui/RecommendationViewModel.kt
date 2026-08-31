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
import com.chloemlla.cdict.core.data.RecommendationItem
import com.chloemlla.cdict.core.data.STUDY_STATUS_LEARNING
import com.chloemlla.cdict.core.data.STUDY_STATUS_MASTERED
import com.chloemlla.cdict.core.data.STUDY_STATUS_REVIEW
import com.chloemlla.cdict.core.data.StudyDao
import com.chloemlla.cdict.core.data.StudyDatabase
import com.chloemlla.cdict.core.data.StudyWordEntity
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** 所有会改动 [queue] 的入口共用这把锁：并发入口各持一份 occupied 快照会抽出重复词。 */
    private val feedMutex = Mutex()
    private var feedJob: Job? = null
    private var masteredJob: Job? = null

    /** 计数与队列必须在同一个日期语义下整体翻页，否则跨零点后进度归零、队列又被补满一天的量。 */
    private var sessionDate: String = today
    private var dictGeneration: Int = DictionaryRepository.generation.value

    init {
        launchFeed {
            if (!openDatabases()) {
                _state.value = RecommendationScreenState.NoDictionary
                return@launchFeed
            }
            buildFeed()
        }
        observeDictionaryGeneration()
    }

    /** 打开两个库并载入可选标签；返回 false 表示词库不可用，错误页的「重试加载」会再走一次。 */
    private suspend fun openDatabases(): Boolean = try {
        val opened = (DictionaryRepository(context).open() as? DatabaseState.Ready)?.database
        if (opened == null) {
            false
        } else {
            val study = studyDb ?: StudyDatabase.open(context)
            dictDb = opened
            studyDb = study
            dictGeneration = DictionaryRepository.generation.value
            _availableTags.value = opened.dictionaryDao().distinctCurriculumTags()
                .flatMap(::parseCurriculumTags)
                .distinct()
                .sorted()
            // collect 永不返回，必须独立 launch，否则下面的首次建流不会执行。
            if (masteredJob == null) masteredJob = launchGuarded { observeMastered(study.studyDao()) }
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /**
     * 词典页「重建词库」会关掉旧实例并删除旧文件，探索页必须重取 DAO 再重建整条流，
     * 否则会一直读已被 unlink 的旧词库。
     */
    private fun observeDictionaryGeneration() {
        viewModelScope.launch {
            DictionaryRepository.generation.collect { generation ->
                if (generation == dictGeneration) return@collect
                dictGeneration = generation
                dictDb = null
                reload()
            }
        }
    }

    /**
     * 查询失败（磁盘写满、词库被重建关掉、文件损坏）必须落成可见状态：裸 launch 里的未捕获异常
     * 会直接终止进程。
     */
    private fun launchGuarded(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_state.value is RecommendationScreenState.Ready) {
                emit()
            } else {
                _state.value = RecommendationScreenState.NoDictionary
            }
        }
    }

    /** 会重建或补足队列的入口：先让上一次生成停下，再独占地跑这一次。 */
    private fun launchFeed(block: suspend () -> Unit) {
        val previous = feedJob
        feedJob = launchGuarded {
            previous?.cancelAndJoin()
            feedMutex.withLock { block() }
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
            feedMutex.withLock {
                queue.retainAll { it.word.id !in mastered }
                topUpToQuota()
                emitAndHydrate()
            }
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
        // 生成期间必须先把页面置成加载态：词池小的时候要等数秒，旧卡片还留在屏幕上就会被继续操作，
        // 而它对应的词已经不在新范围里了。
        _state.value = RecommendationScreenState.Loading
        launchFeed { buildFeed() }
    }

    /** 依据当前每日目标从头重建整条推荐流（点刷新时调用）。 */
    fun reload() {
        _state.value = RecommendationScreenState.Loading
        launchFeed {
            if (dictDb == null || studyDb == null) {
                if (!openDatabases()) {
                    _state.value = RecommendationScreenState.NoDictionary
                    return@launchFeed
                }
            }
            buildFeed()
        }
    }

    /**
     * 回到推荐标签时的轻量同步：今日进度以 study.db 为准重算，剔除已在背词页处理过的词，
     * 再补足到今日剩余额度。不重建整条流，用户当前看到的卡片不会被换掉。
     */
    fun syncFromStore() {
        if (_state.value !is RecommendationScreenState.Ready) return
        launchFeed {
            val dao = studyDb?.studyDao() ?: return@launchFeed
            if (today != sessionDate) {
                buildFeed()
                return@launchFeed
            }
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
        sessionDate = today
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
        enqueueUnique(
            eng.generateFeed(shortfall, today, occupied, scope.curriculumTag, scope.frequencyGroup).items,
        )
    }

    /**
     * 入队兜底：任一生成路径漏防重复都会直接打到 UI（[consume] 只删一份，宽屏预览列表同 key 会崩），
     * 队列长度也不能随「再来一批」无界增长。
     */
    private fun enqueueUnique(items: List<RecommendationItem>) {
        val present = queue.mapTo(mutableSetOf()) { it.word.id }
        for (item in items) {
            if (queue.size >= DAILY_GOAL_MAX) break
            if (present.add(item.word.id)) queue.addLast(RecommendationItemCard(item.word, item.pool))
        }
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
        launchGuarded {
            feedMutex.withLock {
                if (queue.none { it.word.id == id }) return@withLock
                val dao = studyDb?.studyDao() ?: return@withLock
                val existing = dao.word(id)
                // 已在复习阶梯上的词不能重写：upsert 是整行 REPLACE，会把 ease / repetitions /
                // nextReviewDate 清零，几轮记忆进度静默丢失。
                val inPipeline = existing?.status == STUDY_STATUS_LEARNING ||
                    existing?.status == STUDY_STATUS_REVIEW
                if (!inPipeline) {
                    dao.upsert(
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
                }
                consume(id)
            }
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
        launchGuarded {
            feedMutex.withLock {
                if (queue.none { it.word.id == id }) return@withLock
                val dao = studyDb?.studyDao() ?: return@withLock
                dao.upsert(
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
        contextCache.remove(id)
        handledToday = studyDb?.studyDao()?.learnedTodayCount(today) ?: handledToday
        topUpToQuota()
        emitAndHydrate()
    }

    /** 稍后再看：打散插回到当前位置之后第 [DEFER_INSERT_SLOT] 位（队尾不足则回尾）。 */
    fun defer() {
        val cur = _state.value as? RecommendationScreenState.Ready ?: return
        val head = cur.items.firstOrNull() ?: return
        val id = head.word.id
        launchGuarded {
            feedMutex.withLock {
                // 快照来自上一次 emit，队列可能已被后台同步清空或重排；按 id 定位而不是 removeFirst。
                val idx = queue.indexOfFirst { it.word.id == id }
                if (idx < 0) return@withLock
                val item = queue.removeAt(idx)
                queue.add(minOf(idx + DEFER_INSERT_SLOT, queue.size), item)
                emitAndHydrate()
            }
        }
    }

    /** 再来一批：词池学光后按目标再生成一批（终极复习 / 补充）。 */
    fun continueMore() {
        launchFeed {
            val eng = engine() ?: return@launchFeed
            val scope = savedScope()
            val occupied = consumedToday + queue.map { it.word.id }
            val feed = eng.generateFeed(dailyGoal(), today, occupied, scope.curriculumTag, scope.frequencyGroup)
            enqueueUnique(feed.items)
            emitAndHydrate()
        }
    }

    /** 修改每日目标：调高则补足到新额度，调低则从队尾裁剪（PRD 无缝扩充）。 */
    fun setGoal(value: Int) {
        val goal = value.coerceIn(DAILY_GOAL_MIN, DAILY_GOAL_MAX)
        val prev = dailyGoal()
        prefs.edit { putInt(REC_PREF_KEY_GOAL, goal) }
        if (_state.value !is RecommendationScreenState.Ready) return
        launchFeed {
            if (goal > prev) {
                topUpToQuota()
            } else if (goal < prev) {
                // Goal is the day's total target; already-handled words still count
                // toward it, so trim the queue to what remains to be shown.
                val remaining = (goal - handledToday).coerceAtLeast(0)
                repeat((queue.size - remaining).coerceAtLeast(0)) {
                    contextCache.remove(queue.removeLast().word.id)
                }
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

    override fun onCleared() {
        // dict.db 是进程级共享实例，只关自己开的 study.db。
        studyDb?.close()
        studyDb = null
        dictDb = null
        super.onCleared()
    }
}

class RecommendationViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RecommendationViewModel(context.applicationContext as Application) as T
    }
}