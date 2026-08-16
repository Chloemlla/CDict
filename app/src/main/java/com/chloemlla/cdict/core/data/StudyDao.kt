package com.chloemlla.cdict.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: StudyWordEntity)

    @Query("DELETE FROM study_words WHERE wordId = :wordId")
    suspend fun delete(wordId: Long)

    @Query("SELECT wordId FROM study_words")
    suspend fun allStudiedIds(): List<Long>

    @Query("SELECT * FROM study_words WHERE wordId = :wordId")
    suspend fun word(wordId: Long): StudyWordEntity?

    // Words studied today (first learning day) regardless of their current memory state,
    // so progress counts them even after they have been promoted to the review ladder.
    @Query(
        "SELECT COUNT(*) FROM study_words WHERE status IN ('learning', 'review') AND learnedDate = :date",
    )
    suspend fun learnedTodayCount(date: String): Int

    @Query("SELECT wordId FROM study_words WHERE status IN ('learning', 'review') AND learnedDate = :date ORDER BY addedAt")
    suspend fun learnedTodayIds(date: String): List<Long>

    // Words whose next review is due today (or overdue after an absence). Ordered by how
    // decayed they are (oldest scheduled date first, fewest repetitions first) so the
    // most fragile words are reviewed before the cap drops the tail of the backlog.
    @Query(
        "SELECT * FROM study_words WHERE status IN ('learning', 'review') AND nextReviewDate <= :date " +
            "ORDER BY nextReviewDate, repetitions LIMIT :limit",
    )
    suspend fun pendingReview(date: String, limit: Int): List<StudyWordEntity>

    // Weakest-memory words (lowest ease, fewest repetitions) — the ultimate-review fallback
    // target when the fresh-word pool has been exhausted (PRD 边缘策略).
    @Query(
        "SELECT * FROM study_words ORDER BY ease ASC, repetitions ASC, nextReviewDate ASC LIMIT :limit",
    )
    suspend fun weakestStudied(limit: Int): List<StudyWordEntity>

    // ASR scheduling write: promote memory state, record the next review's interval, and
    // derive the repeating ladder through ease / repetition count.
    @Query(
        "UPDATE study_words SET status = :status, nextReviewDate = :nextReviewDate, " +
            "ease = :ease, repetitions = :repetitions, lastInterval = :interval WHERE wordId = :wordId",
    )
    suspend fun schedule(
        wordId: Long,
        status: String,
        nextReviewDate: String,
        ease: Double,
        repetitions: Int,
        interval: Int,
    )

    @Query("SELECT wordId FROM study_words WHERE status = 'mastered'")
    fun masteredIds(): Flow<List<Long>>
}