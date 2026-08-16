package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StudyWordEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        fun open(context: Context): StudyDatabase =
            Room.databaseBuilder(context, StudyDatabase::class.java, "study.db")
                // The ASR ladder (v2) added nextReviewDate/ease/repetitions/lastInterval to
                // study_words. Installed v1 databases cannot be read under the new schema and
                // carry no irreversible data worth hand-migrating, so drop & recreate them
                // rather than crashing on Room's identity-hash validation.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}