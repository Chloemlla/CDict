package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StudyWordEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        // v1 -> v2 introduced the ASR ladder (nextReviewDate/ease/repetitions/lastInterval).
        // v1 rows carry no schedule at all, so the table is recreated with the v2 shape instead
        // of being back-filled column by column.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS study_words")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS study_words (" +
                        "wordId INTEGER NOT NULL, status TEXT NOT NULL, learnedDate TEXT, " +
                        "nextReviewDate TEXT, ease REAL NOT NULL, repetitions INTEGER NOT NULL, " +
                        "lastInterval INTEGER NOT NULL, addedAt INTEGER NOT NULL, masteredAt INTEGER, " +
                        "PRIMARY KEY (wordId))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_study_status_next ON study_words (status, nextReviewDate)",
                )
            }
        }

        // v2 -> v3 adds lastReviewedDate (the same-day scheduling gate) and lapses (failed-recall
        // counter behind the ease penalty). Both are additive, so months of study progress survive
        // the upgrade — this database must never fall back to a destructive migration again.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_words ADD COLUMN lastReviewedDate TEXT")
                db.execSQL("ALTER TABLE study_words ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun open(context: Context): StudyDatabase =
            Room.databaseBuilder(context, StudyDatabase::class.java, "study.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
