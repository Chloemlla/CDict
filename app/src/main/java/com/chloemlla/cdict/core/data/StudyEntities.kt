package com.chloemlla.cdict.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted per-word study state, living in a dedicated `study.db` so the read-only
 * dictionary asset is never mutated or migrated.
 *
 * A word that has no row is brand new and eligible for the daily recommendation.
 * Once a row exists the word is withdrawn from the new-word pool. [status] refines
 * how it is treated afterwards:
 *  - "learned": studied with 我已背会; it must be reviewed the following day.
 *  - "free":   studied while free-flipping (not counted, no forced review).
 *  - "mastered": explicitly marked 已掌握 from the dictionary; never re-queued.
 */
@Entity(
    tableName = "study_words",
    indices = [Index(value = ["status", "learnedDate"], name = "idx_study_status_date")],
)
data class StudyWordEntity(
    @PrimaryKey val wordId: Long,
    val status: String,
    val learnedDate: String?,
    val reviewed: Boolean = false,
    val addedAt: Long = 0L,
    val masteredAt: Long? = null,
)

const val STUDY_STATUS_LEARNED = "learned"
const val STUDY_STATUS_FREE = "free"
const val STUDY_STATUS_MASTERED = "mastered"