package com.chloemlla.cdict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.ui.CdictApp
import com.chloemlla.cdict.ui.DictionaryViewModel
import com.chloemlla.cdict.ui.DictionaryViewModelFactory
import com.chloemlla.cdict.ui.TranslationViewModel
import com.chloemlla.cdict.ui.TranslationViewModelFactory

class MainActivity : ComponentActivity() {
    private val dictionaryViewModel: DictionaryViewModel by viewModels {
        DictionaryViewModelFactory(applicationContext)
    }
    private val translationViewModel: TranslationViewModel by viewModels {
        TranslationViewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by dictionaryViewModel.state.collectAsStateWithLifecycle()
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CdictApp(
                        dictionaryState = state,
                        onDictionaryQueryChanged = dictionaryViewModel::search,
                        onDictionarySelect = dictionaryViewModel::select,
                        onDictionaryPlayPronunciation = dictionaryViewModel::playPronunciation,
                        translationViewModel = translationViewModel,
                    )
                }
            }
        }
    }
}
