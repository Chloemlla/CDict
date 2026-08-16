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
        WordRelationEntity::class,
        WordFormEntity::class,
        EtymologyEntity::class,
        StudyNoteEntity::class,
        WordSearchEntity::class,
        MetadataEntity::class,
    ],
    version = 4,
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

        // v3 -> v4 adds the headwordSummary column and the distribution enrichment
        // tables (word_relations, word_forms, etymologies, study_notes). The DDL is
        // identical to what the merge workflow creates in the published asset, so
        // upgraded installs and fresh createFromAsset installs share one schema.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE words ADD COLUMN headwordSummary TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS word_relations (" +
                        "wordId INTEGER NOT NULL, relationType TEXT NOT NULL, targetWord TEXT NOT NULL, " +
                        "PRIMARY KEY (wordId, relationType, targetWord), " +
                        "FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_word_relations_wordId ON word_relations (wordId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS word_forms (" +
                        "wordId INTEGER NOT NULL, formIndex INTEGER NOT NULL, formText TEXT NOT NULL, formTags TEXT NOT NULL, " +
                        "PRIMARY KEY (wordId, formIndex), " +
                        "FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_word_forms_wordId ON word_forms (wordId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS etymologies (" +
                        "wordId INTEGER NOT NULL, etymologyIndex INTEGER NOT NULL, text TEXT NOT NULL, " +
                        "PRIMARY KEY (wordId, etymologyIndex), " +
                        "FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_etymologies_wordId ON etymologies (wordId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS study_notes (" +
                        "wordId INTEGER NOT NULL, noteIndex INTEGER NOT NULL, noteText TEXT NOT NULL, " +
                        "PRIMARY KEY (wordId, noteIndex), " +
                        "FOREIGN KEY (wordId) REFERENCES words (id) ON UPDATE NO ACTION ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_notes_wordId ON study_notes (wordId)")
            }
        }

        fun open(context: Context): DictionaryDatabase =
            Room.databaseBuilder(context, DictionaryDatabase::class.java, "dict.db")
                .createFromAsset("dict.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
