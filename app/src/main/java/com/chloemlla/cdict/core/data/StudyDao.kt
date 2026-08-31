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

    @Query("SELECT wordId FROM study_words WHERE lastReviewedDate = :date")
    suspend fun reviewedTodayIds(date: String): List<Long>

    // Words whose next review is due today (or overdue after an absence). Ordered by how
    // decayed they are (oldest scheduled date first, fewest repetitions first) so the
    // most fragile words are reviewed before the cap drops the tail of the backlog.
    @Query(
        "SELECT * FROM study_words WHERE status IN ('learning', 'review') AND nextReviewDate <= :date " +
            "ORDER BY nextReviewDate, repetitions LIMIT :limit",
    )
    suspend fun pendingReview(date: String, limit: Int): List<StudyWordEntity>

    // ASR scheduling write: promote memory state, record the next review's interval, and
    // derive the repeating ladder through ease / repetition count. [reviewedDate] is the
    // same-day gate — a word already advanced today must not advance again.
    @Query(
        "UPDATE study_words SET status = :status, nextReviewDate = :nextReviewDate, " +
            "ease = :ease, repetitions = :repetitions, lastInterval = :interval, " +
            "lastReviewedDate = :reviewedDate WHERE wordId = :wordId",
    )
    suspend fun schedule(
        wordId: Long,
        status: String,
        nextReviewDate: String,
        ease: Double,
        repetitions: Int,
        interval: Int,
        reviewedDate: String,
    )

    // Failed recall: knock the ladder back to its first rung, penalise ease and count the lapse.
    @Query(
        "UPDATE study_words SET status = :status, nextReviewDate = :nextReviewDate, ease = :ease, " +
            "repetitions = 0, lastInterval = 1, lapses = :lapses WHERE wordId = :wordId",
    )
    suspend fun lapse(
        wordId: Long,
        status: String,
        nextReviewDate: String,
        ease: Double,
        lapses: Int,
    )

    // Pushes a due word one day out without touching its ladder — used for words that cannot
    // currently be turned into a question, which would otherwise hold the front of the
    // due-date ordering forever and starve every other overdue word.
    @Query("UPDATE study_words SET nextReviewDate = :nextReviewDate WHERE wordId = :wordId")
    suspend fun postpone(wordId: Long, nextReviewDate: String)

    // 已掌握切换只改状态：整行 REPLACE 会把攒下的复习阶梯清零，删行更会连学习历史一起丢掉。
    @Query("UPDATE study_words SET status = 'mastered', masteredAt = :masteredAt WHERE wordId = :wordId")
    suspend fun markMastered(wordId: Long, masteredAt: Long)

    // Cancelling 已掌握 returns the word to the review pipeline. A row that was inserted straight
    // as mastered has no schedule, so it becomes due today rather than falling out of both the
    // review queue and the new-word pool.
    @Query(
        "UPDATE study_words SET status = 'review', masteredAt = NULL, " +
            "nextReviewDate = COALESCE(nextReviewDate, :dueDate) WHERE wordId = :wordId",
    )
    suspend fun unmarkMastered(wordId: Long, dueDate: String)

    @Query("SELECT wordId FROM study_words WHERE status = 'mastered'")
    fun masteredIds(): Flow<List<Long>>
}
