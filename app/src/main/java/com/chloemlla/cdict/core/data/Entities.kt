package com.chloemlla.cdict.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val sortOrder: Int,
)

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val id: Long,
    val word: String,
    val phoneticUk: String? = null,
    val phoneticUs: String? = null,
    val translation: String? = null,
    val definition: String? = null,
    val mnemonic: String? = null,
    val frequencyGroup: Int = 0,
    val frequency: Int = 0,
)

@Entity(tableName = "derived_terms", primaryKeys = ["wordId", "term"])
data class DerivedTermEntity(val wordId: Long, val term: String)

@Entity(tableName = "roots", primaryKeys = ["wordId", "root"])
data class RootEntity(val wordId: Long, val root: String, val meaning: String? = null)

@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey val id: Long,
    val english: String,
    val chinese: String? = null,
)

@Entity(tableName = "word_sentence_links", primaryKeys = ["wordId", "sentenceId"])
data class WordSentenceLinkEntity(val wordId: Long, val sentenceId: Long)

@Entity(tableName = "heatmap_entries", primaryKeys = ["wordId", "period"])
data class HeatmapEntryEntity(val wordId: Long, val period: String, val score: Double)

@Fts4(contentEntity = WordEntity::class)
@Entity(tableName = "word_search")
data class WordSearchEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val word: String,
    val translation: String?,
    val definition: String?,
)

data class WordWithRelations(
    val word: WordEntity,
    val derivedTerms: List<String>,
    val roots: List<RootEntity>,
    val sentences: List<SentenceEntity>,
    val heatmap: List<HeatmapEntryEntity>,
)
