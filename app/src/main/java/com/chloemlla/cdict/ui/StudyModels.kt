package com.chloemlla.cdict.ui

import com.chloemlla.cdict.core.data.WordEntity

enum class StudyPhase { REVIEW, LEARN, DONE, FREE_PLAY }

/** Result of the last review answer, driving green/red feedback in the UI. */
data class ReviewFeedback(
    val correct: Boolean,
    val correctText: String,
    val chosenText: String?,
)

/** A single next-day review multiple-choice question: English word, 4 Chinese options. */
data class ReviewQuestion(
    val wordId: Long,
    val english: String,
    val phonetic: String?,
    val options: List<String>,
    val correctIndex: Int,
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

/** Loose near-duplicate guard so a distractor never reads as the same sense. */
private fun isNearDuplicate(a: String, b: String): Boolean =
    a == b || a.contains(b) || b.contains(a)

/**
 * Draws up to three distinct Chinese-translation distractors, preferring words that
 * share the correct answer's part of speech and are not near-duplicates. Falls back
 * to any distinct translation when a same-POS pool is too thin.
 */
fun buildReviewDistractors(
    correctPos: String,
    pool: List<WordEntity>,
    correctText: String,
    k: Int = 3,
): List<String> {
    val usable = pool.mapNotNull { it.translation?.takeIf(String::isNotBlank) }
    val candidates = if (correctPos.isNotBlank()) {
        val same = usable.filter {
            primaryPartOfSpeech(it) == correctPos && !isNearDuplicate(it, correctText)
        }
        if (same.size >= k) same else same + usable.filterNot { isNearDuplicate(it, correctText) }
    } else {
        usable.filterNot { isNearDuplicate(it, correctText) }
    }
    val result = mutableListOf<String>()
    for (option in candidates.distinct()) {
        if (result.size >= k) break
        result.add(option)
    }
    // Last-resort fill so the question always has enough options.
    var fallbackIndex = 0
    while (result.size < k && fallbackIndex < pool.size) {
        val candidate = pool[fallbackIndex].translation?.takeIf(String::isNotBlank)
        if (candidate != null && !isNearDuplicate(candidate, correctText) && candidate !in result) {
            result.add(candidate)
        }
        fallbackIndex++
    }
    return result
}