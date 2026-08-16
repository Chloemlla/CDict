package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StudyWordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        fun open(context: Context): StudyDatabase =
            Room.databaseBuilder(context, StudyDatabase::class.java, "study.db").build()
    }
}