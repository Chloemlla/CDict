package com.chloemlla.cdict.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted per-word study state, living in a dedicated `study.db` so the read-only
 * dictionary asset is never mutated or migrated.
 *
 * A word that has no row is brand new and eligible for the daily recommendation.
 * Once a row exists the word is withdrawn from the new-word pool. [status] drives the
 * adaptive spaced-repetition (ASR) memory state machine:
 *  - "learning":   newly learned with 我已背会; first review is scheduled for tomorrow.
 *  - "review":     answered correctly at review; [nextReviewDate] schedules the next one
 *                  on the 1 / 3 / 7 / 15 / 30-day ladder, advancing with [repetitions].
 *  - "mastered":   reached the top of the review ladder (or explicitly marked 已掌握 from
 *                  the dictionary); never re-queued.
 *  - "free":       studied while free-flipping (not counted, no forced review).
 */
@Entity(
    tableName = "study_words",
    indices = [Index(value = ["status", "nextReviewDate"], name = "idx_study_status_next")],
)
data class StudyWordEntity(
    @PrimaryKey val wordId: Long,
    val status: String,
    val learnedDate: String?,
    val nextReviewDate: String? = null,
    val ease: Double = 2.5,
    val repetitions: Int = 0,
    val lastInterval: Int = 0,
    val addedAt: Long = 0L,
    val masteredAt: Long? = null,
    // Day the word last advanced the ladder. The immediate test can be replayed without limit,
    // so a same-day gate is what stops five rounds from promoting a word straight to mastered.
    val lastReviewedDate: String? = null,
    val lapses: Int = 0,
)

const val STUDY_STATUS_LEARNING = "learning"
const val STUDY_STATUS_REVIEW = "review"
const val STUDY_STATUS_MASTERED = "mastered"
const val STUDY_STATUS_FREE = "free"