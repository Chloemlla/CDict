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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    Study("背词", "CDict 背词"),
    Dictionary("词典", "CDict 词典"),
    Translation("翻译", "CDict 翻译"),
}

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
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val studyState by studyViewModel.state.collectAsStateWithLifecycle()
    val masteredIds by studyViewModel.masteredIds.collectAsStateWithLifecycle()
    // System back (button or edge-swipe gesture) walks up the tab stack toward Study,
    // mirroring bottom-navigation back behaviour.
    BackHandler(enabled = selectedTab > 0) {
        selectedTab -= 1
    }
    val destination = when (selectedTab) {
        0 -> CdictDestination.Study
        1 -> CdictDestination.Dictionary
        else -> CdictDestination.Translation
    }
    val needsStatusBarPadding = selectedTab == 1 && dictionaryState !is DictionaryScreenState.Ready

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = destination.paneTitle },
        // Each destination owns its top app bar. The shell owns the bottom navigation,
        // so consume its padding once before composing the nested destination scaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            CdictNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .then(if (needsStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
        ) {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxSize(),
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
            ) { tab ->
                when (tab) {
                    0 -> StudyScreen(
                        state = studyState,
                        onReload = studyViewModel::reload,
                        onAnswer = studyViewModel::answerReview,
                        onAdvance = studyViewModel::advanceAfterFeedback,
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
                        onToggleMastered = studyViewModel::toggleMastered,
                    )
                    else -> TranslateScreen(translationViewModel)
                }
            }
        }
    }
}

@Composable
private fun CdictNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val selectedDestination = when (selectedTab) {
        0 -> CdictDestination.Study
        1 -> CdictDestination.Dictionary
        else -> CdictDestination.Translation
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
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = null,
                        )
                    },
                    label = { Text(CdictDestination.Study.label, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = itemColors,
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .semantics {
                            role = Role.Tab
                        },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                        )
                    },
                    label = { Text(CdictDestination.Dictionary.label, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = itemColors,
                    modifier = Modifier
                        .heightIn(min = 64.dp)
                        .semantics {
                            role = Role.Tab
                        },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = null,
                        )
                    },
                    label = { Text(CdictDestination.Translation.label, maxLines = 1) },
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
