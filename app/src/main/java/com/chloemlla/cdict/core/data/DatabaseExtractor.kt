package com.chloemlla.cdict.core.data

import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Outcome of preparing the offline dictionary file; each failure needs a different user action. */
sealed interface ExtractionResult {
    data object Ok : ExtractionResult

    sealed interface Failure : ExtractionResult

    data class InsufficientStorage(val requiredBytes: Long) : Failure

    data object AssetMissing : Failure

    /** The bundled asset or the extracted file cannot be read as a dictionary. */
    data class Corrupted(val detail: String?) : Failure
}

/**
 * Extracts the Brotli-compressed [dict.db.br] from assets on first launch
 * so the APK ships a much smaller asset (8–10 MB vs 90+ MB).
 *
 * Once extracted, subsequent launches skip decompression and use the cached
 * file directly. The file lives at [context.getDatabasePath("dict.db")] so
 * Room's [androidx.room.RoomDatabase.Builder] finds it without
 * [androidx.room.RoomDatabase.Builder.createFromAsset].
 *
 * ### Content-aware auto-rebuild
 * The installed database is re-extracted only when the bundled `dict.signature`
 * disagrees with the `metadata.assetSignature` row inside it, so app updates that
 * ship the same dictionary keep the extracted file.
 *
 * ### Storage guard
 * Decompression needs room for the whole database plus its journal.
 * [ensureDatabaseExists] checks [File.usableSpace] before starting.
 *
 * ### Performance
 * - The compressed asset is loaded with [AssetManager.ACCESS_BUFFER] so all
 *   subsequent reads hit an in-memory buffer instead of JNI streaming.
 * - Output is wrapped in a [BufferedOutputStream] to coalesce writes.
 * - Buffer size is 256 KB to balance loop count and per-call overhead.
 */
object DatabaseExtractor {

    /** Room's `@Index` cannot carry a collation, so the case-insensitive headword index is raw SQL. */
    const val CREATE_WORD_NOCASE_INDEX =
        "CREATE INDEX IF NOT EXISTS idx_words_word_nocase ON words (word COLLATE NOCASE)"

    private const val TAG = "DatabaseExtractor"
    private const val COMPRESSED_ASSET = "dict.db.br"
    private const val BUFFER_SIZE = 256 * 1024 // 256 KB
    private const val MIN_STORAGE_THRESHOLD = 120L * 1024 * 1024 // 120 MB

    private val extractionMutex = Mutex()

    /**
     * Returns the database file that Room will open. It may or may not exist
     * yet — call [ensureDatabaseExists] first.
     */
    fun databaseFile(context: Context): File =
        context.getDatabasePath("dict.db")

    /**
     * Returns true when at least [MIN_STORAGE_THRESHOLD] bytes are available
     * on the filesystem that will hold the decompressed database.
     */
    private fun hasSufficientStorage(context: Context): Boolean {
        val directory = databaseFile(context).parentFile ?: context.filesDir
        return try {
            directory.usableSpace >= MIN_STORAGE_THRESHOLD
        } catch (_: Exception) {
            // If we can't determine available space, proceed anyway.
            true
        }
    }

    private sealed interface Probe {
        /** Opened and the dictionary table has rows; [assetSignature] is the content stamp, if present. */
        class Usable(val assetSignature: String?) : Probe

        /** Opened, but there is no dictionary content in it — safe to delete and extract again. */
        data object Unusable : Probe

        /** Could not be opened at all, so nothing can be concluded about its content. */
        data object Undecidable : Probe
    }

    /**
     * Opens the file read-write on purpose: Room leaves the header in WAL mode, and a WAL
     * database without its `-shm` sidecar cannot be opened read-only at all. Judging such a
     * file "invalid" is what used to delete and re-extract the dictionary on every cold start.
     */
    private fun probe(dbFile: File): Probe =
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                try {
                    if (!database.hasDictionaryRows()) Probe.Unusable else Probe.Usable(database.assetSignature())
                } catch (_: SQLiteException) {
                    Probe.Unusable
                }
            }
        } catch (_: SQLiteDatabaseCorruptException) {
            Probe.Unusable
        } catch (_: SQLiteException) {
            Probe.Undecidable
        }

    private fun SQLiteDatabase.hasDictionaryRows(): Boolean =
        rawQuery("SELECT 1 FROM words LIMIT 1", null).use { cursor -> cursor.moveToFirst() }

    private fun SQLiteDatabase.assetSignature(): String? =
        rawQuery("SELECT value FROM metadata WHERE key = 'assetSignature'", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeIf { it.isNotBlank() } else null
        }

    /**
     * An installed dictionary is stale only when both sides carry a signature and they differ.
     * A missing signature on either side leaves the file alone; [DictionaryUpdateManager] then
     * offers the manual rebuild instead of deleting 94 MB on a guess.
     */
    private fun isStale(context: Context, installedSignature: String?): Boolean {
        if (installedSignature == null) return false
        val bundled = DictionaryUpdateManager.bundledSignature(context) ?: return false
        return bundled != installedSignature
    }

    /**
     * Creates a Brotli decoder for [input].
     *
     * Uses the pure-Java [BrotliInputStream] from the `org.brotli:dec` library.
     * The native [android.util.BrotliInputStream] (API 30+) would be 2-3× faster
     * but is not available under the current compile SDK, so the library fallback
     * is used for all API levels. Decompression is a one-time operation on first
     * launch, so the small performance difference is negligible.
     */
    private fun brotliDecoder(input: InputStream): InputStream =
        BrotliInputStream(input)

    /** Decompresses [dict.db.br] from assets when the installed dictionary is missing or stale. */
    suspend fun ensureDatabaseExists(context: Context): ExtractionResult =
        extractionMutex.withLock {
            withContext(Dispatchers.IO) {
                val dbFile = databaseFile(context)

                if (dbFile.exists()) {
                    when (val probed = probe(dbFile)) {
                        Probe.Undecidable -> return@withContext ExtractionResult.Ok
                        Probe.Unusable -> context.deleteDatabase("dict.db")
                        is Probe.Usable ->
                            if (isStale(context, probed.assetSignature)) {
                                context.deleteDatabase("dict.db")
                            } else {
                                return@withContext ExtractionResult.Ok
                            }
                    }
                }

                // Ensure parent directory exists before checking its filesystem space.
                dbFile.parentFile?.mkdirs()

                if (!hasSufficientStorage(context)) {
                    return@withContext ExtractionResult.InsufficientStorage(MIN_STORAGE_THRESHOLD)
                }

                // Write to a temporary file so an interrupted extraction never
                // leaves a half-written dict.db that Room would open as corrupt.
                val tmpFile = File(dbFile.parentFile, "dict.db.tmp")
                try {
                    tmpFile.delete()
                    context.assets.open(COMPRESSED_ASSET, AssetManager.ACCESS_BUFFER).use { assetInput ->
                        brotliDecoder(assetInput).use { brotli ->
                            tmpFile.outputStream().use { rawOut ->
                                BufferedOutputStream(rawOut, BUFFER_SIZE).use { output ->
                                    brotli.copyTo(output, bufferSize = BUFFER_SIZE)
                                }
                            }
                        }
                    }
                    // Atomic rename — on the same filesystem, this is instant.
                    if (!tmpFile.renameTo(dbFile)) {
                        // Fallback: copy and delete (e.g. across mount points).
                        dbFile.outputStream().use { out ->
                            tmpFile.inputStream().use { tmpInput ->
                                tmpInput.copyTo(out)
                            }
                        }
                    }
                    prepareExtracted(context, dbFile)
                } catch (e: FileNotFoundException) {
                    Log.e(TAG, "Missing $COMPRESSED_ASSET in assets", e)
                    context.deleteDatabase("dict.db")
                    ExtractionResult.AssetMissing
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to extract $COMPRESSED_ASSET", e)
                    // The partial temp file is still on disk here, so a write that ran out of
                    // space is still visible as "not enough space" rather than as corruption.
                    val outOfSpace = !hasSufficientStorage(context)
                    context.deleteDatabase("dict.db")
                    if (outOfSpace) {
                        ExtractionResult.InsufficientStorage(MIN_STORAGE_THRESHOLD)
                    } else {
                        ExtractionResult.Corrupted(e.message)
                    }
                } finally {
                    tmpFile.delete()
                }
            }
        }

    /**
     * Adds the case-insensitive headword index the published asset may not carry yet and
     * confirms the freshly written file really holds the dictionary.
     */
    private fun prepareExtracted(context: Context, dbFile: File): ExtractionResult {
        val ready = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.execSQL(CREATE_WORD_NOCASE_INDEX)
                database.hasDictionaryRows()
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Extracted dictionary is not readable", e)
            false
        }
        if (ready) return ExtractionResult.Ok
        context.deleteDatabase("dict.db")
        return ExtractionResult.Corrupted(null)
    }
}
