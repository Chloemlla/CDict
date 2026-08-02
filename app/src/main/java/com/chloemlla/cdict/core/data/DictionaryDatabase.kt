package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GroupEntity::class,
        WordEntity::class,
        DerivedTermEntity::class,
        RootEntity::class,
        SentenceEntity::class,
        WordSentenceLinkEntity::class,
        HeatmapEntryEntity::class,
        WordSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        fun open(context: Context): DictionaryDatabase =
            Room.databaseBuilder(context, DictionaryDatabase::class.java, "dict.db")
                .createFromAsset("dict.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
