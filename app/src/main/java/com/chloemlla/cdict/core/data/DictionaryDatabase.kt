package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        MetadataEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        // v1 -> v2 adds the AI annotation fields (emotionColor, register, nuanceDescription,
        // usageWarning, collocations) as nullable columns; existing installs keep their data
        // while freshly generated assets carry the columns from the start.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN emotionColor TEXT")
                db.execSQL("ALTER TABLE words ADD COLUMN register TEXT")
                db.execSQL("ALTER TABLE words ADD COLUMN nuanceDescription TEXT")
                db.execSQL("ALTER TABLE words ADD COLUMN usageWarning TEXT")
                db.execSQL("ALTER TABLE words ADD COLUMN collocations TEXT")
            }
        }

        // v2 -> v3 adds the aiSupplemented column that flags which word fields were
        // enriched from the distribution merge, so the UI can show an AI marker.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN aiSupplemented TEXT")
            }
        }

        fun open(context: Context): DictionaryDatabase =
            Room.databaseBuilder(context, DictionaryDatabase::class.java, "dict.db")
                .createFromAsset("dict.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
