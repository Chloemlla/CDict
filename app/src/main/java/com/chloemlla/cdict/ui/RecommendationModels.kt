package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.data.HeatmapEntryEntity
import com.chloemlla.cdict.core.data.RecommendationPool
import com.chloemlla.cdict.core.data.STUDY_STATUS_FREE
import com.chloemlla.cdict.core.data.STUDY_STATUS_LEARNING
import com.chloemlla.cdict.core.data.STUDY_STATUS_MASTERED
import com.chloemlla.cdict.core.data.STUDY_STATUS_REVIEW
import com.chloemlla.cdict.core.data.SentenceEntity
import com.chloemlla.cdict.core.data.WordEntity

/** 单条推荐卡片的词池中文标签（方案A：核心 5 / 派生拓展 3 / 高频过渡 2）。 */
fun recommendationPoolLabel(pool: RecommendationPool): String = when (pool) {
    RecommendationPool.CORE_NEW -> "核心新词"
    RecommendationPool.EXPANSION -> "派生拓展"
    RecommendationPool.SIMPLE -> "高频过渡"
}

/** 单条推荐：词 + 所属词池（用于 UI 打标签）。 */
data class RecommendationItemCard(
    val word: WordEntity,
    val pool: RecommendationPool,
    val studyState: RecommendationStudyState = RecommendationStudyState.NEW,
    /** null 表示阅读上下文还没加载；探索流只为队首附近的卡片查库。 */
    val reading: RecommendationReadingContext? = null,
)

/** 探索卡的阅读上下文：例句与真题热度。助记直接取 [WordEntity.mnemonic]，无需查询。 */
data class RecommendationReadingContext(
    val sentences: List<SentenceEntity> = emptyList(),
    val heatmap: List<HeatmapEntryEntity> = emptyList(),
)

/** 卡面绑定的 study.db 实时状态；[NEW] 即 study_words 里还没有该词的行。 */
enum class RecommendationStudyState { NEW, LEARNING, REVIEW, MASTERED, FREE }

fun recommendationStudyState(status: String?): RecommendationStudyState = when (status) {
    STUDY_STATUS_LEARNING -> RecommendationStudyState.LEARNING
    STUDY_STATUS_REVIEW -> RecommendationStudyState.REVIEW
    STUDY_STATUS_MASTERED -> RecommendationStudyState.MASTERED
    STUDY_STATUS_FREE -> RecommendationStudyState.FREE
    else -> RecommendationStudyState.NEW
}

fun recommendationStudyStateLabel(state: RecommendationStudyState): String = when (state) {
    RecommendationStudyState.NEW -> "未学"
    RecommendationStudyState.LEARNING -> "学习中"
    RecommendationStudyState.REVIEW -> "复习中"
    RecommendationStudyState.MASTERED -> "已掌握"
    RecommendationStudyState.FREE -> "自由练习"
}

sealed interface RecommendationScreenState {
    data object Loading : RecommendationScreenState
    data object NoDictionary : RecommendationScreenState

    /** [items] 为首的元素即当前卡片，其余为后续队列；空队列表示“今日推荐已看完”。 */
    data class Ready(
        val dailyGoal: Int,
        val items: List<RecommendationItemCard>,
        val handledToday: Int,
        val scope: StudyScope = StudyScope(),
        val availableCurriculumTags: List<String> = emptyList(),
    ) : RecommendationScreenState
}