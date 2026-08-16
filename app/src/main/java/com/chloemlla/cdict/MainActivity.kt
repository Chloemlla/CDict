package com.chloemlla.cdict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.ui.CdictApp
import com.chloemlla.cdict.ui.CdictTheme
import com.chloemlla.cdict.ui.DictionaryViewModel
import com.chloemlla.cdict.ui.DictionaryViewModelFactory
import com.chloemlla.cdict.ui.RecommendationViewModel
import com.chloemlla.cdict.ui.RecommendationViewModelFactory
import com.chloemlla.cdict.ui.StudyViewModel
import com.chloemlla.cdict.ui.StudyViewModelFactory
import com.chloemlla.cdict.ui.TranslationViewModel
import com.chloemlla.cdict.ui.TranslationViewModelFactory
import com.chloemlla.lumen.crash.ui.LumenCrashGate

class MainActivity : ComponentActivity() {
    private val dictionaryViewModel: DictionaryViewModel by viewModels {
        DictionaryViewModelFactory(applicationContext)
    }
    private val translationViewModel: TranslationViewModel by viewModels {
        TranslationViewModelFactory(applicationContext)
    }
    private val studyViewModel: StudyViewModel by viewModels {
        StudyViewModelFactory(applicationContext)
    }
    private val recommendationViewModel: RecommendationViewModel by viewModels {
        RecommendationViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumenCrashGate {
                val state by dictionaryViewModel.state.collectAsStateWithLifecycle()
                CdictTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CdictApp(
                            dictionaryState = state,
                            onDictionaryQueryChanged = dictionaryViewModel::search,
                            onDictionarySelect = dictionaryViewModel::select,
                            onDictionaryDeselect = dictionaryViewModel::deselect,
                            onDictionaryPlayPronunciation = dictionaryViewModel::playPronunciation,
                            onDictionaryLoadMore = dictionaryViewModel::loadMore,
                            onDictionarySortModeChanged = dictionaryViewModel::setSortMode,
                            onDictionaryRebuild = dictionaryViewModel::rebuildDictionary,
                            onDictionaryDismissUpdate = dictionaryViewModel::dismissUpdate,
                            translationViewModel = translationViewModel,
                            studyViewModel = studyViewModel,
                            recommendationViewModel = recommendationViewModel,
                        )
                    }
                }
            }
        }
    }
}
