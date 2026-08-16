package com.chloemlla.cdict.core.data

import kotlinx.coroutines.flow.first

/** 推荐流的三种词池（PRD 3:5:2 黄金配比）。 */
enum class RecommendationPool { REVIEW, CORE_NEW, SIMPLE }

/** 单条推荐：一个词 + 它所在的词池。 */
data class RecommendationItem(
    val word: WordEntity,
    val pool: RecommendationPool,
)

/** 一次生成的整条推荐流。 */
data class RecommendationFeed(
    val items: List<RecommendationItem>,
    val generatedForGoal: Int,
)

/**
 * 离线推荐引擎（core/data 层）。完全本地、低 CPU/内存：只用现有 DAO 查询，不新增表
 * 结构，在 dict.db + study.db 之间分池加权抽样。一次 [generateFeed] 即拉出符合
 * “复习 30% / 核心新词 50% / 简单 20%”的一条推荐流，并把已学 / 今日已处理词排除在外。
 *
 * - 组（frequencyGroup）语义：1 = 真题高频核心 … 7 = 生僻低频，组号越小越常见。
 * - “简单/过渡”词取【组 1】（绝对高频、极常见）作为心流缓冲，避免纯生词导致挫败。
 */
class RecommendationEngine(
    private val dictDao: DictionaryDao,
    private val studyDao: StudyDao,
) {

    /**
     * 生成 [goal] 条推荐：
     *  1) REVIEW  （30%）：study.db 中遗忘曲线到期 / 昨日错题的待复习词；
     *  2) SIMPLE  （20%）：未学的绝对高频词（组 1），给流式心流体验；
     *  3) CORE_NEW（其余）：用户目标雅思频率组（组 1..3）的未学新词，高频优先。
     * 冷启动（整库未学）退回“组 1 最常见词”，确保 3 秒可直接开刷；全池学光后自动进入
     * “终极复习模式”，从记忆强度最弱的已学词补足余量。
     */
    suspend fun generateFeed(goal: Int, today: String, excludedIds: Set<Long>): RecommendationFeed {
        if (goal <= 0) return RecommendationFeed(emptyList(), goal)

        val studied = studyDao.allStudiedIds().toMutableSet()
        studied.addAll(excludedIds)

        // 冷启动：从未学过任何词，直接给“组 1 最常见”的前 goal 个词。
        if (studied.isEmpty()) {
            val cold = dictDao.browseGroup(1, goal, 0).first()
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
            return RecommendationFeed(cold.take(goal), goal)
        }

        val used = studied.toMutableSet()
        val reviewCount = goal * 3 / 10
        val simpleCount = goal * 2 / 10
        // 核心新词吸收取整余量，保证全流恰好 = goal。
        val coreCount = goal - reviewCount - simpleCount

        val items = mutableListOf<RecommendationItem>()

        if (reviewCount > 0) {
            val cap = (goal * REVIEW_CAP_MULTIPLIER).toInt().coerceAtLeast(reviewCount)
            val dueIds = studyDao.pendingReview(today, cap).map { it.wordId }
            if (dueIds.isNotEmpty()) {
                val byId = dictDao.wordsByIds(dueIds).associateBy { it.id }
                val picked = dueIds.asSequence()
                    .mapNotNull { byId[it] }
                    .filter { used.add(it.id) }
                    .take(reviewCount)
                    .toList()
                items += picked.map { RecommendationItem(it, RecommendationPool.REVIEW) }
            }
        }

        if (simpleCount > 0) {
            items += sampleGroup(simpleCount, SIMPLE_GROUP, used)
                .map { RecommendationItem(it, RecommendationPool.SIMPLE) }
        }

        val coreGap = goal - items.size
        if (coreGap > 0) {
            items += sampleTargetGroups(coreGap, TARGET_GROUPS, used)
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
        }

        // 全池学光：用记忆强度最弱的已学词补足缺额（终极复习模式）。
        var shortfall = goal - items.size
        if (shortfall > 0) {
            val weakest = studyDao.weakestStudied(shortfall + used.size)
            for (row in weakest) {
                if (shortfall <= 0) break
                val word = dictDao.wordsByIds(listOf(row.wordId)).firstOrNull() ?: continue
                if (items.any { it.word.id == word.id }) continue
                items += RecommendationItem(word, RecommendationPool.REVIEW)
                shortfall--
            }
        }

        return RecommendationFeed(items.take(goal), goal)
    }

    /** 目标频率组抽样：向更靠前的组（更核心）倾斜，各组内用随机广度优先填充，不足再全域兜底。 */
    private suspend fun sampleTargetGroups(
        n: Int,
        groups: IntArray,
        used: MutableSet<Long>,
    ): List<WordEntity> {
        if (n <= 0) return emptyList()
        val out = mutableListOf<WordEntity>()
        for (index in groups.indices) {
            if (out.size >= n) break
            val remainingGroups = groups.size - index
            val share = maxOf(1, (n - out.size) / remainingGroups)
            out += sampleGroup(share, groups[index], used)
        }
        if (out.size < n) {
            out += sampleGroup(n - out.size, SIMPLE_GROUP, used)
        }
        if (out.size < n) {
            var attempts = 0
            while (out.size < n && attempts < SAMPLE_ATTEMPTS) {
                for (w in dictDao.randomWords(600)) {
                    if (out.size >= n) break
                    if (used.add(w.id)) out.add(w)
                }
                attempts++
            }
        }
        return out
    }

    /** 从 [group] 组随机抽 [target] 个未用过（且未学过）的词；组内随机即“频率加权 + 随机扰动”。 */
    private suspend fun sampleGroup(target: Int, group: Int, used: MutableSet<Long>): List<WordEntity> {
        if (target <= 0) return emptyList()
        val out = mutableListOf<WordEntity>()
        var attempts = 0
        while (out.size < target && attempts < SAMPLE_ATTEMPTS) {
            for (w in dictDao.randomWordsInGroup(group, 400)) {
                if (out.size >= target) break
                if (used.add(w.id)) out.add(w)
            }
            attempts++
        }
        return out
    }

    private companion object {
        val TARGET_GROUPS = intArrayOf(1, 2, 3)
        const val SIMPLE_GROUP = 1
        const val REVIEW_CAP_MULTIPLIER = 2.5
        const val SAMPLE_ATTEMPTS = 8
    }
}