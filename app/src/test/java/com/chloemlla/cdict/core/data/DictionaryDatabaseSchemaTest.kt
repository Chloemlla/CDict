package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
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
        val extractionResults = runBlocking {
            coroutineScope {
                List(3) { async { DatabaseExtractor.ensureDatabaseExists(context) } }.awaitAll()
            }
        }
        assertTrue(extractionResults.all { it })

        val db = DictionaryDatabase.open(context)
        db.openHelper.writableDatabase
        assertTrue(db.dictionaryDao().count() > 0)
    }
}
