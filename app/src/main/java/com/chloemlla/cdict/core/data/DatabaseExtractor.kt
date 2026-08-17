package com.chloemlla.cdict.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import java.io.File

/**
 * Extracts the Brotli-compressed [dict.db.br] from assets on first launch
 * so the APK ships a much smaller asset (8–10 MB vs 90+ MB).
 *
 * Once extracted, subsequent launches skip decompression and use the cached
 * file directly. The file lives at [context.getDatabasePath("dict.db")] so
 * Room's [androidx.room.RoomDatabase.Builder] finds it without
 * [androidx.room.RoomDatabase.Builder.createFromAsset].
 */
object DatabaseExtractor {

    private const val COMPRESSED_ASSET = "dict.db.br"

    /**
     * Returns the database file that Room will open. It may or may not exist
     * yet — call [ensureDatabaseExists] first.
     */
    fun databaseFile(context: Context): File =
        context.getDatabasePath("dict.db")

    /**
     * Decompresses [dict.db.br] from [assets] on first install.
     *
     * Writes to a temporary file first, then atomically renames to the final
     * path, so a kill mid-decompression never leaves a corrupt database.
     *
     * @return true when the database is ready (either freshly extracted or
     *         already present), false on failure.
     */
    suspend fun ensureDatabaseExists(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val dbFile = databaseFile(context)
            if (dbFile.exists()) return@withContext true

            // Ensure parent directory exists.
            dbFile.parentFile?.mkdirs()

            // Write to a temporary file so an interrupted extraction never
            // leaves a half-written dict.db that Room would open as corrupt.
            val tmpFile = File(dbFile.parentFile, "dict.db.tmp")
            try {
                context.assets.open(COMPRESSED_ASSET).use { assetInput ->
                    BrotliInputStream(assetInput).use { brotli ->
                        tmpFile.outputStream().use { output ->
                            brotli.copyTo(output, bufferSize = 64 * 1024)
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
                    tmpFile.delete()
                }
                true
            } catch (e: Exception) {
                // Clean up partial output on failure.
                tmpFile.delete()
                false
            }
        }
}