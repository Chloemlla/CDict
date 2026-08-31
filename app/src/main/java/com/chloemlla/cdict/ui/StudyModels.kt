package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.data.WordEntity
import kotlin.math.abs

// LEARN_QUIZ 是新词卡点「我已背会」后的当堂检测：背词页的职责是记忆闭环（提取练习），
// 纯浏览输入交给探索页，所以「我已背会」只是一道门槛，答对才入库并计入今日进度。
enum class StudyPhase { REVIEW, LEARN, LEARN_QUIZ, DONE, FREE_PLAY }

/** Result of the last review answer, driving green/red feedback in the UI. */
data class ReviewFeedback(
    val correct: Boolean,
    val correctText: String,
    val chosenText: String?,
)

/**
 * A single review multiple-choice question: English word, Chinese options.
 *
 * [attempt]/[forceReveal]/[confusionRetry] carry the Error-Attribution Engine's retry
 * plan (优化项五). Each wrong answer bumps [attempt] so the presenter knows it is a new
 * showing; [forceReveal] marks a 完全陌生 case that must re-show the 释义 card before the
 * options; [confusionRetry] marks a 形近混淆 case that pins the same option set for a
 * focused re-discrimination pass. [frequencyGroup] rides along so the scheduling write does
 * not have to query dict.db again for every answered question.
 */
data class ReviewQuestion(
    val wordId: Long,
    val english: String,
    val phonetic: String?,
    val options: List<String>,
    val correctIndex: Int,
    val frequencyGroup: Int,
    val attempt: Int = 0,
    val forceReveal: Boolean = false,
    val confusionRetry: Boolean = false,
) {
    val correctText: String get() = options[correctIndex]
}

sealed interface StudyScreenState {
    data object Loading : StudyScreenState
    data class NoDictionary(val message: String) : StudyScreenState
    data class Ready(
        val dailyGoal: Int,
        val todayDone: Int,
        val phase: StudyPhase,
        val reviewTotal: Int = 0,
        val reviewRemaining: Int = 0,
        val question: ReviewQuestion? = null,
        val feedback: ReviewFeedback? = null,
        val card: WordEntity? = null,
        val queueRemaining: Int = 0,
        val learnedToday: List<WordEntity> = emptyList(),
        // True when the review phase is an on-demand test of today's newly-learned words
        // instead of yesterday's scheduled due queue; drives the header label.
        val isImmediateTest: Boolean = false,
        val scope: StudyScope = StudyScope(),
        val availableCurriculumTags: List<String> = emptyList(),
        // 一次性提示（写入失败、队列已到底…），下一次状态推送即清空。
        val notice: String? = null,
        // 「我已背会」的入库 / 组题在途：两个按钮都要停下，否则推迟的词会被拉去做检测。
        val busy: Boolean = false,
    ) : StudyScreenState
}

/**
 * Best-effort part-of-speech extraction from a Chinese translation. Dictionary
 * translations conventionally open with an abbreviation like `n. `, `vt. ` or
 * `adj. `; verb sub-forms are collapsed to a single "v" bucket. Unknown tags
 * (or translations beginning directly with Chinese) yield empty, which the
 * review distractor builder treats as a wildcard.
 */
private val POS_LEADING = Regex("""^([a-zA-Z]+)\.?""")

fun primaryPartOfSpeech(chinese: String): String {
    if (chinese.isBlank()) return ""
    val raw = POS_LEADING.find(chinese.trim())?.groupValues?.get(1)?.lowercase() ?: return ""
    return when (raw) {
        "n" -> "n"
        "v", "vt", "vi", "verb" -> "v"
        "adj" -> "adj"
        "adv" -> "adv"
        "prep" -> "prep"
        "conj" -> "conj"
        "pron" -> "pron"
        "num" -> "num"
        "art" -> "art"
        else -> ""
    }
}

/**
 * Drops a leading part-of-speech tag (`n. `, `vt. ` …) from a translation so similarity
 * is measured on the actual Chinese sense rather than the shared abbreviation prefix.
 */
private val POS_TAG_PREFIX = Regex("""^[a-zA-Z]+\.\s*""")

private fun stripPosTag(zh: String): String = zh.replaceFirst(POS_TAG_PREFIX, "")

/** Character bigrams; a single remaining character yields itself. */
private fun bigrams(s: String): Set<String> =
    if (s.length < 2) {
        if (s.isEmpty()) emptySet() else setOf(s)
    } else {
        (0 until s.length - 1).map { s.substring(it, it + 2) }.toSet()
    }

/**
 * Offline stand-in for a semantic-vector cosine. Two senses are treated as near-synonyms
 * when their stripped-Chinese bigram sets overlap past a threshold: exact duplicates (or
 * near-duplicates) score high, unrelated words score ~0. Returned in [0, 1].
 */
fun semanticSimilarity(a: String, b: String): Float {
    val ba = bigrams(stripPosTag(a))
    val bb = bigrams(stripPosTag(b))
    if (ba.isEmpty() || bb.isEmpty()) return 0f
    val union = (ba union bb).size
    if (union == 0) return 0f
    return (ba intersect bb).size.toFloat() / union
}

/** Edit distance between two strings; small values flag orthographically confusable words. */
fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + cost,
            )
        }
        for (j in 0..b.length) previous[j] = current[j]
    }
    return previous[b.length]
}

// Dynamic Distractor Engine (优化项一): candidate distractors must survive a strict
// word-class match, a difficulty band around the target, and a semantic filter that
// discards near-synonyms; the survivors are weighted toward orthographically confusable
// distractors (Levenshtein <= 2) which force a sharper discrimination.
private data class DistractorCandidate(
    val word: WordEntity,
    val label: String,
    val bandDistance: Int,
    val orthoWeight: Int,
)

/**
 * Builds up to [k] distinct Chinese-translation distractors for [target]:
 *
 *  A. word-class strict match: only distractors sharing [primaryPartOfSpeech] are first
 *     choice, so an easy ADJ/A is never confused with a verb target;
 *  B. difficulty decay (PRD §3.2): strictly prefer the same [frequencyGroup] first, then the
 *     ±1 band, falling back outward only when that neighbourhood is too thin;
 *  C. semantic-vector filter: discard any candidate whose [semanticSimilarity] to the
 *     correct sense exceeds the 0.65 confusion ceiling;
 *  D. orthographic weighting: nearer-spelling distractors (Levenshtein <= 2) rank first.
 */
fun buildReviewDistractors(
    target: WordEntity,
    pool: List<WordEntity>,
    k: Int = 3,
): List<String> {
    // 与出题处同一套回退：只有 definition 的词也要能凑出选项，否则它永远出不了题，
    // 又因为到期日最旧而恒排复习窗口最前，把其它到期词全饿死。
    val correctText = target.translation?.takeIf(String::isNotBlank)
        ?: target.definition?.takeIf(String::isNotBlank) ?: return emptyList()
    val pos = primaryPartOfSpeech(correctText)
    val targetGroup = target.frequencyGroup

    val candidates = pool.mapNotNull { w ->
        val label = w.translation?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        DistractorCandidate(
            word = w,
            label = label,
            bandDistance = abs(w.frequencyGroup - targetGroup),
            orthoWeight = if (levenshtein(w.word, target.word) <= 2) 1 else 0,
        )
    }.distinctBy { it.label }

    val picked = linkedSetOf<String>()
    fun take(src: List<DistractorCandidate>) {
        for (c in src) {
            if (picked.size >= k) break
            if (semanticSimilarity(c.label, correctText) > 0.65f) continue
            picked.add(c.label)
        }
    }

    fun byOrtho(list: List<DistractorCandidate>) =
        list.sortedWith(compareByDescending<DistractorCandidate> { it.orthoWeight })

    // Strictest layer (PRD §3.2): same frequencyGroup AND strict word-class match — options
    // come from the exact difficulty neighbourhood so the question is fair.
    take(
        byOrtho(
            candidates.filter { primaryPartOfSpeech(it.label) == pos && it.bandDistance == 0 },
        ),
    )
    // Same word class within the ±1 band, allowing the same difficulty neighbourhood to fill up.
    if (picked.size < k) {
        take(byOrtho(candidates.filter { primaryPartOfSpeech(it.label) == pos && it.bandDistance == 1 }))
    }
    // Relax the difficulty band, keep the word-class match.
    if (picked.size < k) {
        take(byOrtho(candidates.filter { primaryPartOfSpeech(it.label) == pos }))
    }
    // Last resort: any distinct, non-synonymous translation.
    if (picked.size < k) {
        take(byOrtho(candidates))
    }
    return picked.toList().take(k)
}