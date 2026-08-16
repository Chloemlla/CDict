package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.data.RecommendationPool
import com.chloemlla.cdict.core.data.WordEntity

/** 单条推荐卡片的词池中文标签。 */
fun recommendationPoolLabel(pool: RecommendationPool): String = when (pool) {
    RecommendationPool.REVIEW -> "复习巩固"
    RecommendationPool.CORE_NEW -> "核心新词"
    RecommendationPool.SIMPLE -> "简单过渡"
}

/** 单条推荐：词 + 所属词池（用于 UI 打标签）。 */
data class RecommendationItemCard(
    val word: WordEntity,
    val pool: RecommendationPool,
)

sealed interface RecommendationScreenState {
    data object Loading : RecommendationScreenState
    data object NoDictionary : RecommendationScreenState

    /** [items] 为首的元素即当前卡片，其余为后续队列；空队列表示“今日推荐已看完”。 */
    data class Ready(
        val dailyGoal: Int,
        val totalCount: Int,
        val items: List<RecommendationItemCard>,
        val handledToday: Int,
    ) : RecommendationScreenState
}