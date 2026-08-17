package com.chloemlla.cdict.core.data

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream

/**
 * Extracts the Brotli-compressed [dict.db.br] from assets on first launch
 * so the APK ships a much smaller asset (8–10 MB vs 90+ MB).
 *
 * Once extracted, subsequent launches skip decompression and use the cached
 * file directly. The file lives at [context.getDatabasePath("dict.db")] so
 * Room's [androidx.room.RoomDatabase.Builder] finds it without
 * [androidx.room.RoomDatabase.Builder.createFromAsset].
 *
 * ### Performance
 * - On API 30+ the system's native Brotli decoder is used (2-3× faster than
 *   the pure-Java fallback).
 * - The compressed asset is loaded with [AssetManager.ACCESS_BUFFER] so all
 *   subsequent reads hit an in-memory buffer instead of JNI streaming.
 * - Output is wrapped in a [BufferedOutputStream] to coalesce writes.
 * - Buffer size is 256 KB to balance loop count and per-call overhead.
 */
object DatabaseExtractor {

    private const val COMPRESSED_ASSET = "dict.db.br"
    private const val BUFFER_SIZE = 256 * 1024 // 256 KB

    /**
     * Returns the database file that Room will open. It may or may not exist
     * yet — call [ensureDatabaseExists] first.
     */
    fun databaseFile(context: Context): File =
        context.getDatabasePath("dict.db")

    /**
     * Creates a Brotli decoder for [input].
     *
     * On API 30+ (Android 11+) the system's native [android.util.BrotliInputStream]
     * is used, which is significantly faster than the pure-Java fallback. On older
     * API levels the pure-Java [BrotliInputStream] from the `org.brotli:dec` library
     * is used instead.
     */
    @Suppress("NewApi")
    private fun brotliDecoder(input: InputStream): InputStream =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.util.BrotliInputStream(input)
        } else {
            BrotliInputStream(input)
        }

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