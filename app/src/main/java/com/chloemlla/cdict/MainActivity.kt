package com.chloemlla.cdict

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.ui.CdictApp
import com.chloemlla.cdict.ui.CdictTheme
import com.chloemlla.cdict.ui.about.AboutOverlayHost
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

    // 每次收到外部词条跳转就自增，用作切到词典标签的一次性信号（值本身无意义，只看变化）。
    private var externalWordRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleExternalWord(intent)
        setContent {
            LumenCrashGate {
                val state by dictionaryViewModel.state.collectAsStateWithLifecycle()
                CdictTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AboutOverlayHost {
                            CdictApp(
                                dictionaryState = state,
                                onDictionaryQueryChanged = dictionaryViewModel::search,
                                onDictionarySelect = dictionaryViewModel::select,
                                onDictionaryOpenDerived = dictionaryViewModel::openDerivedWord,
                                onDictionaryDeselect = dictionaryViewModel::deselect,
                                onDictionaryPlayPronunciation = dictionaryViewModel::playPronunciation,
                                onDictionaryLoadMore = dictionaryViewModel::loadMore,
                                onDictionarySortModeChanged = dictionaryViewModel::setSortMode,
                                onDictionaryCurriculumTagChanged = dictionaryViewModel::setCurriculumTag,
                                onDictionaryRebuild = dictionaryViewModel::rebuildDictionary,
                                onDictionaryDismissUpdate = dictionaryViewModel::dismissUpdate,
                                translationViewModel = translationViewModel,
                                studyViewModel = studyViewModel,
                                recommendationViewModel = recommendationViewModel,
                                dictionaryJumpRequest = externalWordRequest,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalWord(intent)
    }

    /** 快速翻译弹窗「前往」带来的词条：交给词典 ViewModel 打开，并请求切到词典标签。 */
    private fun handleExternalWord(intent: Intent?) {
        val word = intent?.getStringExtra(EXTRA_WORD)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        dictionaryViewModel.openExternalWord(word)
        externalWordRequest++
    }

    companion object {
        /** 外部入口要求打开的词条原文（见 quicktranslate.QuickTranslateActivity）。 */
        const val EXTRA_WORD = "com.chloemlla.cdict.extra.WORD"
    }
}
