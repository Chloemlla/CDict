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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun CdictApp(
    dictionaryState: DictionaryScreenState,
    onDictionaryQueryChanged: (String) -> Unit,
    onDictionarySelect: (WordEntity) -> Unit,
    onDictionaryPlayPronunciation: (WordEntity, Accent) -> Unit,
    onDictionaryLoadMore: () -> Unit,
    onDictionarySortModeChanged: (SortMode) -> Unit,
    translationViewModel: TranslationViewModel,
    studyViewModel: StudyViewModel,
    recommendationViewModel: RecommendationViewModel,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val studyState by studyViewModel.state.collectAsStateWithLifecycle()
    val masteredIds by studyViewModel.masteredIds.collectAsStateWithLifecycle()
    val recommendationState by recommendationViewModel.state.collectAsStateWithLifecycle()
    // System back (button or edge-swipe gesture) walks up the tab stack toward Study,
    // mirroring bottom-navigation back behaviour.
    BackHandler(enabled = selectedTab > 0) {
        selectedTab -= 1
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

    val switchTab: (Int) -> Unit = { index -> selectedTab = index }
    // “查看详情 / 切词典”：先让词典选中该词，再切到词典标签页渲染详情。
    val onOpenDictionaryWord: (WordEntity) -> Unit = { word ->
        onDictionarySelect(word)
        selectedTab = CdictDestination.Dictionary.ordinal
    }
    val wideLayout = widthClass != WindowWidthSizeClass.Compact

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
                        dictionaryState = dictionaryState,
                        onDictionaryQueryChanged = onDictionaryQueryChanged,
                        onDictionarySelect = onDictionarySelect,
                        onDictionaryPlayPronunciation = onDictionaryPlayPronunciation,
                        onDictionaryLoadMore = onDictionaryLoadMore,
                        onDictionarySortModeChanged = onDictionarySortModeChanged,
                        translationViewModel = translationViewModel,
                        studyViewModel = studyViewModel,
                        studyState = studyState,
                        masteredIds = masteredIds,
                        recommendationViewModel = recommendationViewModel,
                        recommendationState = recommendationState,
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
                    dictionaryState = dictionaryState,
                    onDictionaryQueryChanged = onDictionaryQueryChanged,
                    onDictionarySelect = onDictionarySelect,
                    onDictionaryPlayPronunciation = onDictionaryPlayPronunciation,
                    onDictionaryLoadMore = onDictionaryLoadMore,
                    onDictionarySortModeChanged = onDictionarySortModeChanged,
                    translationViewModel = translationViewModel,
                    studyViewModel = studyViewModel,
                    studyState = studyState,
                    masteredIds = masteredIds,
                    recommendationViewModel = recommendationViewModel,
                    recommendationState = recommendationState,
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
    dictionaryState: DictionaryScreenState,
    onDictionaryQueryChanged: (String) -> Unit,
    onDictionarySelect: (WordEntity) -> Unit,
    onDictionaryPlayPronunciation: (WordEntity, Accent) -> Unit,
    onDictionaryLoadMore: () -> Unit,
    onDictionarySortModeChanged: (SortMode) -> Unit,
    translationViewModel: TranslationViewModel,
    studyViewModel: StudyViewModel,
    studyState: StudyScreenState,
    masteredIds: Set<Long>,
    recommendationViewModel: RecommendationViewModel,
    recommendationState: RecommendationScreenState,
    onToggleMastered: (WordEntity) -> Unit,
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
        when (t) {
            0 -> StudyScreen(
                state = studyState,
                onReload = studyViewModel::reload,
                onAnswer = studyViewModel::answerReview,
                onAdvance = studyViewModel::advanceAfterFeedback,
                onQuestionPresented = studyViewModel::noteQuestionPresented,
                onDebugLaunchReview = studyViewModel::debugLaunchReview,
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
                onPlayPronunciation = onDictionaryPlayPronunciation,
                onLoadMore = onDictionaryLoadMore,
                onSortModeChanged = onDictionarySortModeChanged,
                masteredIds = masteredIds,
                onToggleMastered = onToggleMastered,
            )
            2 -> TranslateScreen(translationViewModel)
            else -> RecommendationScreen(
                state = recommendationState,
                onReload = recommendationViewModel::reload,
                onMarkLearned = recommendationViewModel::markLearned,
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