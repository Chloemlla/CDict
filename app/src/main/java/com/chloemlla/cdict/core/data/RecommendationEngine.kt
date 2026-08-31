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
    suspend fun generateFeed(
        goal: Int,
        today: String,
        excludedIds: Set<Long>,
        tag: String? = null,
        group: Int? = null,
    ): RecommendationFeed {
        if (goal <= 0) return RecommendationFeed(emptyList(), goal)

        val studied = studyDao.allStudiedIds().toMutableSet()
        val used = (studied + excludedIds).toMutableSet()

        // 冷启动：从未学过任何词，直接给“组 1 最常见”的前 goal 个词。
        // 当指定了课程标签（如”高中短语”）时，该标签下的词可能不在标准雅思频率组
        // （1-7）内，因此先用 browseGroupFiltered 尝试，不足时退回到 randomWordsFiltered
        // 全域随机抽样，保证 feed 不为空。
        if (studied.isEmpty()) {
            val cold = mutableListOf<RecommendationItem>()
            // browseGroup 是确定性排序，补卡时会原封不动地再取回同一批；多取一些后按
            // used 过滤，才能让第二次请求拿到没出现过的词。
            val overFetch = goal * COLD_OVERFETCH
            val head = if (tag == null) {
                dictDao.browseGroup(group ?: 1, overFetch, 0).first()
            } else {
                dictDao.browseGroupFiltered(tag, group ?: 1, overFetch, 0).first()
            }
            for (w in head) {
                if (cold.size >= goal) break
                if (used.add(w.id)) cold += RecommendationItem(w, RecommendationPool.CORE_NEW)
            }
            if (cold.size < goal) {
                for (w in dictDao.randomWordsFiltered(tag, overFetch)) {
                    if (cold.size >= goal) break
                    if (used.add(w.id)) cold += RecommendationItem(w, RecommendationPool.CORE_NEW)
                }
            }
            return RecommendationFeed(cold, goal)
        }

        val coreCount = goal * 5 / 10
        val expansionCount = goal * 3 / 10
        // 高频过渡词吸收取整余量，保证全流恰好 = goal。
        val simpleCount = goal - coreCount - expansionCount

        val items = mutableListOf<RecommendationItem>()

        if (coreCount > 0) {
            items += sampleTargetGroups(coreCount, TARGET_GROUPS, used, tag, group)
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
        }

        if (expansionCount > 0) {
            items += sampleExpansion(expansionCount, studied, used, tag, group)
                .map { RecommendationItem(it, RecommendationPool.EXPANSION) }
        }

        if (simpleCount > 0) {
            items += sampleGroup(simpleCount, SIMPLE_GROUP, used, tag)
                .map { RecommendationItem(it, RecommendationPool.SIMPLE) }
        }

        // 任一池不足：先用目标组核心新词补足，再全域兜底，保证恰好 = goal。
        var shortfall = goal - items.size
        if (shortfall > 0) {
            items += sampleTargetGroups(shortfall, TARGET_GROUPS, used, tag, group)
                .map { RecommendationItem(it, RecommendationPool.CORE_NEW) }
            shortfall = goal - items.size
        }
        if (shortfall > 0) {
            var attempts = 0
            while (items.size < goal && attempts < SAMPLE_ATTEMPTS) {
                val before = items.size
                for (w in dictDao.randomScoped(tag, group, BLANKET_SAMPLE_LIMIT)) {
                    if (items.size >= goal) break
                    if (used.add(w.id)) items += RecommendationItem(w, RecommendationPool.CORE_NEW)
                }
                // 采样池已被 used 吃光时再抽也只会拿回同一批，继续重试纯是全表扫描。
                if (items.size == before) break
                attempts++
            }
        }

        return RecommendationFeed(items.take(goal), goal)
    }

    private suspend fun sampleExpansion(
        target: Int,
        studied: Set<Long>,
        used: MutableSet<Long>,
        tag: String?,
        group: Int?,
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
            val pool = if (tag == null) dictDao.wordsSharingRoots(rootSet.toList(), EXPANSION_QUERY_CAP)
            else dictDao.wordsSharingRootsFiltered(tag, rootSet.toList(), EXPANSION_QUERY_CAP)
            for (w in pool) {
                if (out.size >= target) break
                if (matchesGroup(w, group) && used.add(w.id)) out.add(w)
            }
        }
        if (out.size < target) {
            out += sampleTargetGroups(target - out.size, EXPANSION_FALLBACK_GROUPS, used, tag, group)
        }
        return out
    }

    private suspend fun sampleTargetGroups(
        n: Int,
        groups: IntArray,
        used: MutableSet<Long>,
        tag: String?,
        group: Int?,
    ): List<WordEntity> {
        if (n <= 0) return emptyList()
        // 当用户锁定了具体频率组，目标组收敛为单组，忽视梯度。
        val effectiveGroups = if (group != null) intArrayOf(group) else groups
        val out = mutableListOf<WordEntity>()
        for (index in effectiveGroups.indices) {
            if (out.size >= n) break
            val remainingGroups = effectiveGroups.size - index
            val share = maxOf(1, (n - out.size) / remainingGroups)
            out += sampleGroup(share, effectiveGroups[index], used, tag)
        }
        if (out.size < n) {
            out += sampleGroup(n - out.size, SIMPLE_GROUP, used, tag)
        }
        if (out.size < n) {
            var attempts = 0
            while (out.size < n && attempts < SAMPLE_ATTEMPTS) {
                val before = out.size
                // 锁定频率组时把过滤下推到 SQL：查询后再过滤会让锁定组与随机样本不相交的那几轮全白跑。
                for (w in dictDao.randomScoped(tag, group, BLANKET_SAMPLE_LIMIT)) {
                    if (out.size >= n) break
                    if (used.add(w.id)) out.add(w)
                }
                if (out.size == before) break
                attempts++
            }
        }
        return out
    }

    /** 从 [group] 组随机抽 [target] 个未用过（且未学过）的词；组内随机即“频率加权 + 随机扰动”。 */
    private suspend fun sampleGroup(
        target: Int,
        group: Int,
        used: MutableSet<Long>,
        tag: String?,
    ): List<WordEntity> {
        if (target <= 0) return emptyList()
        val out = mutableListOf<WordEntity>()
        var attempts = 0
        while (out.size < target && attempts < SAMPLE_ATTEMPTS) {
            val before = out.size
            val pool = if (tag == null) dictDao.randomWordsInGroup(group, GROUP_SAMPLE_LIMIT)
            else dictDao.randomWordsInGroupFiltered(tag, group, GROUP_SAMPLE_LIMIT)
            for (w in pool) {
                if (out.size >= target) break
                if (used.add(w.id)) out.add(w)
            }
            if (out.size == before) break
            attempts++
        }
        return out
    }

    /** 词根派生兜底时，若锁定了频率组则只保留该组的词。 */
    private fun matchesGroup(w: WordEntity, group: Int?): Boolean =
        group == null || w.frequencyGroup == group

    private companion object {
        val TARGET_GROUPS = intArrayOf(1, 2, 3)
        val EXPANSION_FALLBACK_GROUPS = intArrayOf(2, 3, 4)
        const val SIMPLE_GROUP = 1
        const val EXPANSION_STUDIED_SAMPLE = 60
        const val EXPANSION_ROOT_CAP = 300
        const val EXPANSION_QUERY_CAP = 400
        const val SAMPLE_ATTEMPTS = 3
        const val GROUP_SAMPLE_LIMIT = 400
        const val BLANKET_SAMPLE_LIMIT = 600
        const val COLD_OVERFETCH = 3
    }
}