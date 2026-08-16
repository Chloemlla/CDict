package com.chloemlla.cdict.core.audio

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Disk LRU cache for pronounced audio (PRD §3.4). Files are keyed by the MD5 of
 * `<accent>:<source>:<text>` so the same word+accent maps to a stable, short filename;
 * the [source] dimension (e.g. "vivo" / "youdao") keeps each pronunciation tier's audio
 * in its own namespace so they never overwrite each other. A 50MB ceiling is enforced by
 * evicting least-recently-played entries (tracked via file mtime) when the folder grows
 * past the limit. Lives in the app cache dir, so it is naturally cleared by the OS under
 * pressure and needs no manual cleanup.
 */
class SpeechAudioCache(context: Context) {
    private val dir = File(context.cacheDir, "speech_cache")

    /** Human-readable cap for the PRD's 50MB budget. */
    fun budget(): Long = MAX_BYTES

    fun find(text: String, accent: Accent, source: String): File? {
        val file = fileFor(text, accent, source)
        if (!file.isFile) return null
        // Touch mtime so LRU eviction counts this as recently used.
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    fun put(text: String, accent: Accent, source: String, bytes: ByteArray): File {
        dir.mkdirs()
        val file = fileFor(text, accent, source)
        file.writeBytes(bytes)
        trim()
        return file
    }

    private fun fileFor(text: String, accent: Accent, source: String): File =
        File(dir, "${md5("${accent.name}:$source:$text").take(16)}.$EXT")

    private fun trim() {
        if (dirUsage() <= MAX_BYTES) return
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
            .sortedBy { it.lastModified() } // oldest first = least recently used
        for (file in files) {
            if (dirUsage() <= MAX_BYTES) break
            file.delete()
        }
    }

    private fun dirUsage(): Long =
        dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_BYTES = 50L * 1024 * 1024
        private const val EXT = "au"
    }
}