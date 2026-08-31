package com.chloemlla.cdict

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.core.net.ClashPartner
import com.chloemlla.cdict.ui.CdictApp
import com.chloemlla.cdict.ui.CdictTheme
import com.chloemlla.cdict.ui.about.AboutOverlayHost
import com.chloemlla.cdict.ui.about.AboutStore
import com.chloemlla.cdict.ui.DictionaryViewModel
import com.chloemlla.cdict.ui.DictionaryViewModelFactory
import com.chloemlla.cdict.ui.RecommendationViewModel
import com.chloemlla.cdict.ui.RecommendationViewModelFactory
import com.chloemlla.cdict.ui.StudyViewModel
import com.chloemlla.cdict.ui.StudyViewModelFactory
import com.chloemlla.cdict.ui.TranslationViewModel
import com.chloemlla.cdict.ui.TranslationViewModelFactory
import com.chloemlla.lumen.crash.ui.LumenCrashGate
import kotlinx.coroutines.flow.first

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

    // 本次进入前台的单调时刻；onPause 时结算成累计前台时长。
    private var foregroundSince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleExternalWord(intent)
        setContent {
            // 拉起 CMFA 的伙伴配对确认窗（透明窗盖在本应用之上，每进程一次）。等 ClashPartner 自己
            // 在 IO 线程探测出伙伴包名后才发起，未安装伙伴应用时不在主线程做 PackageManager 探测。
            LaunchedEffect(Unit) {
                ClashPartner.state.first { it.installedPackage != null }
                ClashPartner.requestPairing(this@MainActivity)
            }
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

    override fun onResume() {
        super.onResume()
        foregroundSince = SystemClock.elapsedRealtime()
    }

    /**
     * 累加本次前台时长。用单调时钟，避免改系统时间把计数拉成负数或天文数字；
     * 划词弹窗是独立的 QuickTranslateActivity，不经过这里，因此不计入使用时长。
     */
    override fun onPause() {
        super.onPause()
        val since = foregroundSince
        if (since == 0L) return
        foregroundSince = 0L
        val elapsed = SystemClock.elapsedRealtime() - since
        if (elapsed <= 0L) return
        val store = AboutStore(this)
        store.foregroundMillis += elapsed
    }

    /** 快速翻译弹窗「前往」带来的词条：交给词典 ViewModel 打开，并请求切到词典标签。 */
    private fun handleExternalWord(intent: Intent?) {
        if (intent == null) return
        val word = intent.getStringExtra(EXTRA_WORD)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        // 取出即消费：Activity 会长期持有这个 Intent，留着它旋转屏幕或恢复进程时会重放这次跳转。
        intent.removeExtra(EXTRA_WORD)
        dictionaryViewModel.openExternalWord(word)
        externalWordRequest++
    }

    companion object {
        /** 外部入口要求打开的词条原文（见 quicktranslate.QuickTranslateActivity）。 */
        const val EXTRA_WORD = "com.chloemlla.cdict.extra.WORD"
    }
}
