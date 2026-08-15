package com.chloemlla.cdict.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryDatabaseSchemaTest {
    @Test
    fun prepackagedDictDbMatchesRoomSchema() {
        // Opening the writable database runs Room's createFromAsset validation
        // against the bundled dict.db; any drift from the entity declarations
        // (columns, defaults, indices, foreign keys) throws here.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = DictionaryDatabase.open(context)
        db.openHelper.writableDatabase
    }
}
