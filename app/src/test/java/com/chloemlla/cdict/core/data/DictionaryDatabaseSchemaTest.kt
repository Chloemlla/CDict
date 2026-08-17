package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryDatabaseSchemaTest {
    @Test
    fun prepackagedDictDbMatchesRoomSchema() {
        // Decompress the Brotli-compressed asset (dict.db.br) first, then open
        // the database. Any drift from the entity declarations (columns, defaults,
        // indices, foreign keys) throws during Room's open validation.
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { DatabaseExtractor.ensureDatabaseExists(context) }
        val db = DictionaryDatabase.open(context)
        db.openHelper.writableDatabase
    }
}
