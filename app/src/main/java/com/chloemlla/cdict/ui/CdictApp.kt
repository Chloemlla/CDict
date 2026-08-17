package com.chloemlla.cdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

private enum class CdictDestination(
    val label: String,
    val paneTitle: String,
    val icon: ImageVector,
) {
    Study("背词", "CDict 背词", Icons.Filled.School),
    Dictionary("词典", "CDict 词典", Icons.AutoMirrored.Filled.MenuBook),
    Translation("翻译", "CDict 翻译", Icons.Filled.Translate),
    Recommendation("推荐", "CDict 推荐", Icons.Filled.Recommend),
}

/** Saver for the tab visit-history stack so it survives configuration changes. */
private val IntListSaver = listSaver<List<Int>, Int>(
    save = { list -> list },
    restore = { it },
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CdictApp(
    dictionaryState: DictionaryScreenState,
    onDictionaryQueryChanged: (String) -> Unit,
    onDictionarySelect: (WordEntity) -> Unit,
    onDictionaryOpenDerived: (WordEntity) -> Unit,
    onDictionaryDeselect: () -> Boolean,
    onDictionaryPlayPronunciation: (WordEntity, Accent) -> Unit,
    onDictionaryLoadMore: () -> Unit,
    onDictionarySortModeChanged: (SortMode) -> Unit,
    onDictionaryCurriculumTagChanged: (String?) -> Unit,
    onDictionaryRebuild: () -> Unit,
    onDictionaryDismissUpdate: () -> Unit,
    translationViewModel: TranslationViewModel,
    studyViewModel: StudyViewModel,
    recommendationViewModel: RecommendationViewModel,
) {
    // 默认落在词典页（主要入口），用户可从底部 / 侧边导航切到其他标签。
    var selectedTab by rememberSaveable { mutableIntStateOf(CdictDestination.Dictionary.ordinal) }
    val studyState by studyViewModel.state.collectAsStateWithLifecycle()
    val masteredIds by studyViewModel.masteredIds.collectAsStateWithLifecycle()
    val recommendationState by recommendationViewModel.state.collectAsStateWithLifecycle()
    // Visit-history stack for the bottom bar: system back returns to the tab the user actually
    // came from (instead of walking the destination enum order toward Study). Empty stack means
    // there is nowhere to go back to, so the default system-back exit applies.
    var navStack by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf<List<Int>>(emptyList()) }
    // Non-null while the dictionary tab is showing a word that was jumped to from another tab
    // (e.g. the recommendation feed), so back closes the detail and returns to the jump origin.
    var dictionaryJumpFrom by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = navStack.isNotEmpty()) {
        selectedTab = navStack.last()
        navStack = navStack.dropLast(1)
    }

    // 响应式导航：窄窗口（COMPACT）用底部导航栏；平板/大屏（MEDIUM/EXPANDED）用侧边导航栏，
    // 推荐页内部也随之切换单列 / 两栏布局。
    val widthClass = LocalContext.current.findActivity()
        ?.let { calculateWindowSizeClass(it).widthSizeClass }
        ?: WindowWidthSizeClass.Compact
    val useRail = widthClass == WindowWidthSizeClass.Medium || widthClass == WindowWidthSizeClass.Expanded

    val destination =
        CdictDestination.entries.getOrElse(selectedTab.coerceIn(0, CdictDestination.entries.lastIndex)) {
            CdictDestination.Study
        }
    val needsStatusBarPadding =
        selectedTab == CdictDestination.Dictionary.ordinal && dictionaryState !is DictionaryScreenState.Ready

    // Manual tab switch records the tab being left so back can return to it. Tapping the
    // dictionary tab explicitly starts a fresh dictionary session (clears any pending jump).
    val switchTab: (Int) -> Unit = { index ->
        if (index != selectedTab) {
            navStack = navStack + selectedTab
            if (index == CdictDestination.Dictionary.ordinal) dictionaryJumpFrom = null
            selectedTab = index
        }
    }
    // “查看详情 / 切词典”：先让词典选中该词，再切到词典标签页渲染详情，并记下跳转来源。
    val onOpenDictionaryWord: (WordEntity) -> Unit = { word ->
        onDictionarySelect(word)
        if (selectedTab != CdictDestination.Dictionary.ordinal) {
            dictionaryJumpFrom = selectedTab
            navStack = navStack + selectedTab
            selectedTab = CdictDestination.Dictionary.ordinal
        }
    }
    // Dictionary detail back: a cross-tab jump returns to where the word was opened from;
    // otherwise back walks the in-detail history (派生词「前往」) and only closes onto the
    // browse list once that history is exhausted.
    val onDictionaryBackFromDetail: () -> Unit = {
        val stillInDetail = onDictionaryDeselect()
        if (!stillInDetail) {
            val origin = dictionaryJumpFrom
            if (origin != null) {
                dictionaryJumpFrom = null
                selectedTab = origin
                navStack = navStack.filterNot { it == origin }
            }
        }
    }
    val wideLayout = widthClass != WindowWidthSizeClass.Compact
    // Keeps each tab's UI state (scroll position, search text, open detail) alive across tab
    // switches so returning to a tab restores exactly where the user left off.
    val tabStateHolder = rememberSaveableStateHolder()

    // Dictionary-update prompt: when the bundled asset's content signature differs from the
    // installed dictionary DB, the user is asked to rebuild so the enriched content takes effect.
    if (dictionaryState is DictionaryScreenState.Ready && dictionaryState.updateNeeded) {
        AlertDialog(
            onDismissRequest = onDictionaryDismissUpdate,
            title = { Text("检测到词典已更新") },
            text = {
                Text("本地词库需要重建以加载新版词典内容。重建不会影响你的背词进度。")
            },
            confirmButton = {
                TextButton(onClick = onDictionaryRebuild) {
                    Text("立即重建")
                }
            },
            dismissButton = {
                TextButton(onClick = onDictionaryDismissUpdate) {
                    Text("稍后再说")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = destination.paneTitle },
        // Each destination owns its top app bar. The shell owns navigation, so consume its
        // padding once before composing the nested destination scaffolds.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!useRail) {
                CdictNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = switchTab,
                )
            }
        },
    ) { innerPadding ->
        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                CdictNavigationRail(
                    selected = destination,
                    onSelect = { switchTab(it.ordinal) },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(if (needsStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
                ) {
                    DestinationContent(
                        tab = selectedTab,
                        wideLayout = wideLayout,
                        stateHolder = tabStateHolder,
                        dictionaryState = dictionaryState,
                        onDictionaryQueryChanged = onDictionaryQueryChanged,
                        onDictionarySelect = onDictionarySelect,
                        onDictionaryOpenDerived = onDictionaryOpenDerived,
                        onDictionaryBackFromDetail = onDictionaryBackFromDetail,
                        onDictionaryPlayPronunciation = onDictionaryPlayPronunciation,
                        onDictionaryLoadMore = onDictionaryLoadMore,
                        onDictionarySortModeChanged = onDictionarySortModeChanged,
                        onDictionaryCurriculumTagChanged = onDictionaryCurriculumTagChanged,
                        translationViewModel = translationViewModel,
                        studyViewModel = studyViewModel,
                        studyState = studyState,
                        masteredIds = masteredIds,
                        recommendationViewModel = recommendationViewModel,
                        recommendationState = recommendationState,
                        onMarkMastered = recommendationViewModel::markMastered,
                        onToggleMastered = { word -> studyViewModel.toggleMastered(word.id) },
                        onOpenDictionaryWord = onOpenDictionaryWord,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .then(if (needsStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
            ) {
                DestinationContent(
                    tab = selectedTab,
                    wideLayout = wideLayout,
                    stateHolder = tabStateHolder,
                    dictionaryState = dictionaryState,
                    onDictionaryQueryChanged = onDictionaryQueryChanged,
                    onDictionarySelect = onDictionarySelect,
                    onDictionaryOpenDerived = onDictionaryOpenDerived,
                    onDictionaryBackFromDetail = onDictionaryBackFromDetail,
                    onDictionaryPlayPronunciation = onDictionaryPlayPronunciation,
                    onDictionaryLoadMore = onDictionaryLoadMore,
                    onDictionarySortModeChanged = onDictionarySortModeChanged,
                    onDictionaryCurriculumTagChanged = onDictionaryCurriculumTagChanged,
                    translationViewModel = translationViewModel,
                    studyViewModel = studyViewModel,
                    studyState = studyState,
                    masteredIds = masteredIds,
                    recommendationViewModel = recommendationViewModel,
                    recommendationState = recommendationState,
                    onMarkMastered = recommendationViewModel::markMastered,
                    onToggleMastered = { word -> studyViewModel.toggleMastered(word.id) },
                    onOpenDictionaryWord = onOpenDictionaryWord,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DestinationContent(
    tab: Int,
    wideLayout: Boolean,
    stateHolder: SaveableStateHolder,
    dictionaryState: DictionaryScreenState,
    onDictionaryQueryChanged: (String) -> Unit,
    onDictionarySelect: (WordEntity) -> Unit,
    onDictionaryOpenDerived: (WordEntity) -> Unit,
    onDictionaryBackFromDetail: () -> Unit,
    onDictionaryPlayPronunciation: (WordEntity, Accent) -> Unit,
    onDictionaryLoadMore: () -> Unit,
    onDictionarySortModeChanged: (SortMode) -> Unit,
    onDictionaryCurriculumTagChanged: (String?) -> Unit,
    translationViewModel: TranslationViewModel,
    studyViewModel: StudyViewModel,
    studyState: StudyScreenState,
    masteredIds: Set<Long>,
    recommendationViewModel: RecommendationViewModel,
    recommendationState: RecommendationScreenState,
    onToggleMastered: (WordEntity) -> Unit,
    onMarkMastered: () -> Unit,
    onOpenDictionaryWord: (WordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = tab,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (
                fadeIn(animationSpec = tween(durationMillis = 220)) +
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = 280),
                        initialOffsetX = { fullWidth -> fullWidth / 5 * direction },
                    )
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 140)) +
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 220),
                            targetOffsetX = { fullWidth -> -fullWidth / 8 * direction },
                        ),
                    ).using(SizeTransform(clip = true))
        },
        label = "Destination transition",
    ) { t ->
        // Save/restore each tab's UI state across switches (scroll, text fields, open detail).
        stateHolder.SaveableStateProvider(key = t) {
        when (t) {
            0 -> StudyScreen(
                state = studyState,
                onReload = studyViewModel::reload,
                onAnswer = studyViewModel::answerReview,
                onAdvance = studyViewModel::advanceAfterFeedback,
                onQuestionPresented = studyViewModel::noteQuestionPresented,
                onDebugLaunchReview = studyViewModel::debugLaunchReview,
                onStartImmediateTest = studyViewModel::startImmediateTest,
                onMarkLearned = studyViewModel::markLearned,
                onDefer = studyViewModel::deferWord,
                onContinueFreePlay = studyViewModel::continueFreePlay,
                onExitFreePlay = studyViewModel::exitFreePlay,
                onSetGoal = studyViewModel::setGoal,
                onPlayPronunciation = onDictionaryPlayPronunciation,
            )
            1 -> DictionaryApp(
                state = dictionaryState,
                onQueryChanged = onDictionaryQueryChanged,
                onSelect = onDictionarySelect,
                onOpenDerivedWord = onDictionaryOpenDerived,
                onBackFromDetail = onDictionaryBackFromDetail,
                onPlayPronunciation = onDictionaryPlayPronunciation,
                onLoadMore = onDictionaryLoadMore,
                onSortModeChanged = onDictionarySortModeChanged,
                onCurriculumTagChanged = onDictionaryCurriculumTagChanged,
                masteredIds = masteredIds,
                onToggleMastered = onToggleMastered,
            )
            2 -> TranslateScreen(translationViewModel)
            else -> RecommendationScreen(
                state = recommendationState,
                onReload = recommendationViewModel::reload,
                onMarkLearned = recommendationViewModel::markLearned,
                onMarkMastered = recommendationViewModel::markMastered,
                onDefer = recommendationViewModel::defer,
                onContinueMore = recommendationViewModel::continueMore,
                onSetGoal = recommendationViewModel::setGoal,
                onOpenWord = onOpenDictionaryWord,
                onPlayPronunciation = onDictionaryPlayPronunciation,
                wideLayout = wideLayout,
            )
        }
        }
    }
}

@Composable
private fun CdictNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val selectedDestination =
        CdictDestination.entries.getOrElse(selectedTab.coerceIn(0, CdictDestination.entries.lastIndex)) {
            CdictDestination.Study
        }
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.semantics {
                paneTitle = "CDict 主导航，当前为 ${selectedDestination.label}"
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CDict",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " · IELTS 词典与翻译",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 0.dp,
            ) {
                CdictDestination.entries.forEachIndexed { index, dest ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(dest.label, maxLines = 1) },
                        alwaysShowLabel = true,
                        colors = itemColors,
                        modifier = Modifier
                            .heightIn(min = 64.dp)
                            .semantics {
                                role = Role.Tab
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun CdictNavigationRail(
    selected: CdictDestination,
    onSelect: (CdictDestination) -> Unit,
) {
    val itemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "CDict",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "IELTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        CdictDestination.entries.forEach { dest ->
            NavigationRailItem(
                selected = dest == selected,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(dest.label, maxLines = 1) },
                alwaysShowLabel = true,
                colors = itemColors,
                modifier = Modifier.semantics {
                    role = Role.Tab
                },
            )
        }
    }
}