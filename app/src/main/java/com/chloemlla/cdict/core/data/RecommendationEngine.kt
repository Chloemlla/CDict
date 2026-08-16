package com.chloemlla.cdict.core.data

import kotlinx.coroutines.flow.first

/** 推荐流的三种词池（方案A · 定位分离：推荐页只做“输入 / 预热”，复习权交还背词页）。 */
enum class RecommendationPool { CORE_NEW, EXPANSION, SIMPLE }

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
 * 离线推荐引擎（core/data 层）。完全本地、低 CPU/内存：只用现有 DAO 查询，不新增表结构，
 * 在 dict.db + study.db 之间分池加权抽样。一次 [generateFeed] 即拉出符合
 * “核心新词 50% / 派生拓展 30% / 高频过渡 20%”的每日探索流，并把已学 / 今日已处理词排除在外。
 *
 * 方案A 定位分离：推荐页不再承担“复习巩固”（复习完全交还背词页），只做轻度阅读与预热，
 * 复习将来由背词页的四选一 / ASR 状态机处理。
 * - 组（frequencyGroup）语义：1 = 真题高频核心 … 7 = 生僻低频，组号越小越常见。
 * - CORE_NEW （5 成）：用户目标雅思频率组（组 1..3）的未学新词，高频优先，带完整上下文。
 * - EXPANSION（3 成）：与已学词共享词根（roots）派生出的新词，建立在熟悉词汇之上；
 *   词根数据稀疏时退回“组 2..4”目标邻域抽样，保证占比仍可成立。
 * - SIMPLE   （2 成）：未学的绝对高频词（组 1），给流式心流体验（高频过渡词 / 真题好句）。
 */
class RecommendationEngine(
    private val dictDao: DictionaryDao,
    private val studyDao: StudyDao,
) {

    /**
     * 生成 [goal] 条推荐：
     *  1) CORE_NEW（50%）：用户目标雅思频率组（组 1..3）的未学新词，高频优先；
     *  2) EXPANSION（30%）：与已学词共享词根的派生拓展新词；
     *  3) SIMPLE  （20%）：未学的绝对高频词（组 1），给流式心流体验。
     * 冷启动（整库未学）退回“组 1 最常见词”，确保 3 秒可直接开刷；任一池不足时用核心新词 /
     * 全域兜底补足，保证全流恰好 = goal。
     */
    suspend fun generateFeed(goal: Int, today: String, excludedIds: Set<Long>): RecommendationFeed {
        if (goal <= 0) return RecommendationFeed(emptyList(), goal)

        val studied = studyDao.allStudiedIds().toMutableSet()
        val used = (studied + excludedIds).toMutableSet()

        // 冷启动：从未学过任何词，直接给“组 1 最常见”的前 goal 个词。
        if (studied.isEmpty()) {
            val cold = dictDao.browseGroup(1, goal, 0).first()
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
            return RecommendationFeed(cold.take(goal), goal)
        }

        val coreCount = goal * 5 / 10
        val expansionCount = goal * 3 / 10
        // 高频过渡词吸收取整余量，保证全流恰好 = goal。
        val simpleCount = goal - coreCount - expansionCount

        val items = mutableListOf<RecommendationItem>()

        if (coreCount > 0) {
            items += sampleTargetGroups(coreCount, TARGET_GROUPS, used)
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
        }

        if (expansionCount > 0) {
            items += sampleExpansion(expansionCount, studied, used)
                .map { RecommendationItem(it, RecommendationPool.EXPANSION) }
        }

        if (simpleCount > 0) {
            items += sampleGroup(simpleCount, SIMPLE_GROUP, used)
                .map { RecommendationItem(it, RecommendationPool.SIMPLE) }
        }

        // 任一池不足：先用目标组核心新词补足，再全域兜底，保证恰好 = goal。
        var shortfall = goal - items.size
        if (shortfall > 0) {
            items += sampleTargetGroups(shortfall, TARGET_GROUPS, used)
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
            shortfall = goal - items.size
        }
        if (shortfall > 0) {
            var attempts = 0
            while (items.size < goal && attempts < SAMPLE_ATTEMPTS) {
                for (w in dictDao.randomWords(600)) {
                    if (items.size >= goal) break
                    if (used.add(w.id)) items += RecommendationItem(w, RecommendationPool.CORE_NEW)
                }
                attempts++
            }
        }

        return RecommendationFeed(items.take(goal), goal)
    }

    /**
     * “派生拓展”词池：从已学单词的共享词根（roots 表）派生新词，把每日探索建立在熟悉词汇之上。
     * 已学集合与结果都设上限，避免对 study / dict 全量遍历；词根数据稀疏或已被学尽时，
     * 退回“组 2..4”目标邻域抽样，保证 30% 占比仍可成立而不必依赖词根表的填充度。
     */
    private suspend fun sampleExpansion(
        target: Int,
        studied: Set<Long>,
        used: MutableSet<Long>,
    ): List<WordEntity> {
        if (target <= 0) return emptyList()
        val out = mutableListOf<WordEntity>()
        val rootSet = linkedSetOf<String>()
        for (id in studied.take(EXPANSION_STUDIED_SAMPLE)) {
            for (root in dictDao.roots(id)) {
                rootSet.add(root.root)
                if (rootSet.size >= EXPANSION_ROOT_CAP) break
            }
            if (rootSet.size >= EXPANSION_ROOT_CAP) break
        }
        if (rootSet.isNotEmpty()) {
            for (w in dictDao.wordsSharingRoots(rootSet.toList(), EXPANSION_QUERY_CAP)) {
                if (out.size >= target) break
                if (used.add(w.id)) out.add(w)
            }
        }
        if (out.size < target) {
            out += sampleTargetGroups(target - out.size, EXPANSION_FALLBACK_GROUPS, used)
        }
        return out
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
        val EXPANSION_FALLBACK_GROUPS = intArrayOf(2, 3, 4)
        const val SIMPLE_GROUP = 1
        const val EXPANSION_STUDIED_SAMPLE = 60
        const val EXPANSION_ROOT_CAP = 300
        const val EXPANSION_QUERY_CAP = 400
        const val SAMPLE_ATTEMPTS = 8
    }
}