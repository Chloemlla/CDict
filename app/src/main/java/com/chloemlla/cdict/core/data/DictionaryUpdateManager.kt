package com.chloemlla.cdict.core.data

import android.content.Context

object DictionaryUpdateManager {

    private const val PREFS = "dict_update"
    private const val KEY_CHECKED_VERSION = "checked_version_code"
    private const val KEY_NEEDS_REBUILD = "needs_rebuild"
    private const val ASSET_SIGNATURE_KEY = "assetSignature"

    /**
     * Read the bundled content signature from [dict.signature] shipped in assets.
     * Returns null when no signature file is present (e.g. debug builds).
     */
    fun bundledSignature(context: Context): String? =
        runCatching {
            context.assets.open("dict.signature").bufferedReader().use { it.readText().trim() }
                .takeIf { it.isNotBlank() }
        }.getOrNull()

    /**
     * Read the installed dictionary DB's [assetSignature] metadata row.
     */
    fun installedSignature(database: DictionaryDatabase): String? =
        runCatching { database.dictionaryDao().metadataValue(ASSET_SIGNATURE_KEY) }.getOrNull()

    /**
     * Check whether the bundled dictionary asset has changed since the last time
     * the app was opened. Results are cached in SharedPreferences keyed by the
     * current versionCode, so the check runs at most once per app update.
     *
     * @return true when the installed dictionary is stale and should be rebuilt.
     */
    fun check(context: Context, database: DictionaryDatabase): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        }.getOrNull() ?: return false

        val checked = prefs.getLong(KEY_CHECKED_VERSION, -1L)
        if (checked == version) {
            return prefs.getBoolean(KEY_NEEDS_REBUILD, false)
        }

        val bundled = bundledSignature(context) ?: return false
        val installed = installedSignature(database)
        val needs = bundled != installed

        prefs.edit()
            .putLong(KEY_CHECKED_VERSION, version)
            .putBoolean(KEY_NEEDS_REBUILD, needs)
            .apply()
        return needs
    }

    /**
     * Mark the dictionary as reconciled (either dismissed or rebuilt) so the
     * prompt does not reappear until the next app update.
     */
    fun markReconciled(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NEEDS_REBUILD, false).apply()
    }
}