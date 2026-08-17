package com.chloemlla.cdict.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["translation"], name = "idx_words_translation"),
        Index(value = ["frequencyGroup", "frequency", "word"], name = "idx_words_group_frequency"),
    ],
)
data class WordEntity(
    @PrimaryKey val id: Long,
    val word: String,
    val phoneticUk: String? = null,
    val phoneticUs: String? = null,
    val translation: String? = null,
    val definition: String? = null,
    val mnemonic: String? = null,
    val emotionColor: String? = null,
    val register: String? = null,
    val nuanceDescription: String? = null,
    val usageWarning: String? = null,
    val collocations: String? = null,
    /** One-sentence gloss of the headword from the distribution merge. */
    val headwordSummary: String? = null,
    /** Comma-separated fields (phoneticUk/phoneticUs/mnemonic/derived/sentences/headwordSummary/relations/forms/etymology/studyNotes) enriched from the AI distribution merge. */
    val aiSupplemented: String? = null,
    /** Comma-separated curriculum labels applied by dictionary workflows. */
    val curriculumTags: String? = null,
    @ColumnInfo(defaultValue = "0") val frequencyGroup: Int = 0,
    @ColumnInfo(defaultValue = "0") val frequency: Int = 0,
)

@Entity(
    tableName = "derived_terms",
    primaryKeys = ["wordId", "term"],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class DerivedTermEntity(val wordId: Long, val term: String)

@Entity(
    tableName = "roots",
    primaryKeys = ["wordId", "root"],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class RootEntity(val wordId: Long, val root: String, val meaning: String? = null)

@Entity(tableName = "sentences")
data class SentenceEntity(
    @PrimaryKey val id: Long,
    val english: String,
    val chinese: String? = null,
)

@Entity(
    tableName = "word_sentence_links",
    primaryKeys = ["wordId", "sentenceId"],
    indices = [
        Index(value = ["wordId"], name = "idx_links_word"),
        Index(value = ["sentenceId"], name = "idx_links_sentence"),
    ],
    foreignKeys = [
        ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"]),
        ForeignKey(entity = SentenceEntity::class, parentColumns = ["id"], childColumns = ["sentenceId"]),
    ],
)
data class WordSentenceLinkEntity(val wordId: Long, val sentenceId: Long)

@Entity(
    tableName = "heatmap_entries",
    primaryKeys = ["wordId", "period"],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class HeatmapEntryEntity(val wordId: Long, val period: String, val score: Double)

/** A single synonym/antonym/related_term relation for a word (from the distribution merge). */
@Entity(
    tableName = "word_relations",
    primaryKeys = ["wordId", "relationType", "targetWord"],
    indices = [Index(value = ["wordId"], name = "index_word_relations_wordId")],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class WordRelationEntity(val wordId: Long, val relationType: String, val targetWord: String)

/** An inflection form of a word; formTags is a comma-joined POS/tense tag list. */
@Entity(
    tableName = "word_forms",
    primaryKeys = ["wordId", "formIndex"],
    indices = [Index(value = ["wordId"], name = "index_word_forms_wordId")],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class WordFormEntity(val wordId: Long, val formIndex: Int, val formText: String, val formTags: String)

/** A structured etymology paragraph for a word (from the distribution merge). */
@Entity(
    tableName = "etymologies",
    primaryKeys = ["wordId", "etymologyIndex"],
    indices = [Index(value = ["wordId"], name = "index_etymologies_wordId")],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class EtymologyEntity(val wordId: Long, val etymologyIndex: Int, val text: String)

/** A learner-oriented study note for a word (from the distribution merge). */
@Entity(
    tableName = "study_notes",
    primaryKeys = ["wordId", "noteIndex"],
    indices = [Index(value = ["wordId"], name = "index_study_notes_wordId")],
    foreignKeys = [ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
)
data class StudyNoteEntity(val wordId: Long, val noteIndex: Int, val noteText: String)

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
