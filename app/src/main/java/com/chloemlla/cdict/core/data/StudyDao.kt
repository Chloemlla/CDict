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

    @Query("SELECT COUNT(*) FROM study_words WHERE status = 'learned' AND learnedDate = :date")
    suspend fun learnedTodayCount(date: String): Int

    @Query("SELECT wordId FROM study_words WHERE status = 'learned' AND learnedDate = :date ORDER BY addedAt")
    suspend fun learnedTodayIds(date: String): List<Long>

    // Words learned on a previous day that have not yet been answered correctly in
    // review. Oldest-learned first (time-gradient backlog) and capped to [limit] so a
    // long absence never dumps an overwhelming review session on the user in one day.
    @Query(
        "SELECT * FROM study_words WHERE status = 'learned' AND learnedDate < :date AND reviewed = 0 " +
            "ORDER BY learnedDate, addedAt LIMIT :limit",
    )
    suspend fun pendingReview(date: String, limit: Int): List<StudyWordEntity>

    @Query("UPDATE study_words SET reviewed = 1 WHERE wordId = :wordId")
    suspend fun markReviewed(wordId: Long)

    @Query("SELECT wordId FROM study_words WHERE status = 'mastered'")
    fun masteredIds(): Flow<List<Long>>
}