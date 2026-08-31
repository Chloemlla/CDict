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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.DictionaryRepository
import com.chloemlla.cdict.core.data.WordEntity
import com.chloemlla.cdict.ui.about.AboutScreenRoute
import com.chloemlla.cdict.ui.about.AboutStore
import com.chloemlla.cdict.ui.about.DonationPromptGate
import com.chloemlla.cdict.ui.about.DonationTipBar
import com.chloemlla.cdict.ui.about.LocalAboutController

// 标签命名区分两页职责（方案A 定位分离）：探索页只做浏览与预热，背词页做四选一与记忆度考核。
private enum class CdictDestination(
    val label: String,
    val paneTitle: String,
    val icon: ImageVector,
) {
    Study("背词", "CDict 背词", Icons.Filled.School),
    Dictionary("词典", "CDict 词典", Icons.AutoMirrored.Filled.MenuBook),
    Translation("翻译", "CDict 翻译", Icons.Filled.Translate),
    Recommendation("探索", "CDict 探索", Icons.Filled.Explore),
}

/** 标签访问历史的 Saver，使其可在配置变更后保留。 */
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
    dictionaryJumpRequest: Int = 0,
) {
    // 默认落在词典页（主要入口），用户可从底部 / 侧边导航切到其他标签。
    var selectedTab by rememberSaveable { mutableIntStateOf(CdictDestination.Dictionary.ordinal) }
    val studyState by studyViewModel.state.collectAsStateWithLifecycle()
    val masteredIds by studyViewModel.masteredIds.collectAsStateWithLifecycle()
    val recommendationState by recommendationViewModel.state.collectAsStateWithLifecycle()
    // 记录实际访问过的标签；每个标签只保留最近一次，历史为空时交还系统处理返回并退出应用。
    var navStack by rememberSaveable(stateSaver = IntListSaver) { mutableStateOf<List<Int>>(emptyList()) }
    // 仅在从其他标签跳转到词典详情时记录来源，以便关闭详情后原路返回。
    var dictionaryJumpFrom by rememberSaveable { mutableStateOf<Int?>(null) }
    // 浮层（关于 / 赞赏 / 更新说明）的返回处理注册得更早，优先级反而最低：浮层可见时内层的返回
    // 处理必须让位，否则第一次按返回只会在浮层背后悄悄切走标签。
    val aboutController = LocalAboutController.current
    BackHandler(enabled = navStack.isNotEmpty() && !aboutController.isOpen) {
        selectedTab = navStack.last()
        navStack = navStack.dropLast(1)
    }

    // 响应式导航：窄窗口（COMPACT）用底部导航栏；平板/大屏（MEDIUM/EXPANDED）用侧边导航栏，
    // 探索页内部也随之切换单列 / 两栏布局。
    val widthClass = LocalContext.current.findActivity()
        ?.let { calculateWindowSizeClass(it).widthSizeClass }
        ?: WindowWidthSizeClass.Compact
    val useRail = widthClass == WindowWidthSizeClass.Medium || widthClass == WindowWidthSizeClass.Expanded

    val destination = CdictDestination.entries[selectedTab.coerceIn(0, CdictDestination.entries.lastIndex)]
    val needsStatusBarPadding =
        selectedTab == CdictDestination.Dictionary.ordinal && dictionaryState !is DictionaryScreenState.Ready

    val haptic = LocalHapticFeedback.current
    // 手动切换标签时保留访问历史，轻触反馈帮助用户确认当前标签已改变。
    // 同一标签在历史里只保留最近一次，避免来回切换后要按很多次返回才能退出应用。
    val switchTab: (Int) -> Unit = { index ->
        if (index != selectedTab) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            navStack = navStack.filterNot { it == selectedTab } + selectedTab
            if (index == CdictDestination.Dictionary.ordinal) dictionaryJumpFrom = null
            selectedTab = index
        }
    }
    // “查看详情 / 切词典”：先让词典选中该词，再切到词典标签页渲染详情，并记下跳转来源。
    val onOpenDictionaryWord: (WordEntity) -> Unit = { word ->
        onDictionarySelect(word)
        if (selectedTab != CdictDestination.Dictionary.ordinal) {
            dictionaryJumpFrom = selectedTab
            navStack = navStack.filterNot { it == selectedTab } + selectedTab
            selectedTab = CdictDestination.Dictionary.ordinal
        }
    }
    // 词典详情返回时，跨标签跳转回到来源标签；派生词详情则优先回退到上一层详情。
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
    // 外部入口（快速翻译弹窗的「前往」）到来时切到词典标签；词条本身由词典 ViewModel 打开。
    // 初值 0 表示本次启动没有外部跳转，此时不抢走用户当前所在的标签。
    LaunchedEffect(dictionaryJumpRequest) {
        if (dictionaryJumpRequest > 0 && selectedTab != CdictDestination.Dictionary.ordinal) {
            dictionaryJumpFrom = null
            navStack = navStack.filterNot { it == selectedTab } + selectedTab
            selectedTab = CdictDestination.Dictionary.ordinal
        }
    }
    // 回到前台也要重新对齐：进程没死却跨过零点时，内存里的「今日」进度还是昨天的。
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 背词与探索共用同一份今日额度与 study.db 状态：切回任一页或回到前台时按库对齐今日进度，
    // 并把已在另一页处理过的词从内存队列剔除，避免同一个词被两页各展示一次。
    LaunchedEffect(selectedTab, resumeTick) {
        when (selectedTab) {
            CdictDestination.Study.ordinal -> studyViewModel.syncFromStore()
            CdictDestination.Recommendation.ordinal -> recommendationViewModel.syncFromStore()
        }
    }
    // 词库重建会关掉旧的数据库实例：世代号变化时让背词与探索重新取 dao 并重建队列，用户不必重启
    // 应用。已同步的世代号存进 rememberSaveable，配置变更不会把同一世代再同步一次（否则旋转屏幕
    // 会把正在做的题清掉）。
    val dictionaryGeneration by DictionaryRepository.generation.collectAsStateWithLifecycle()
    var syncedGeneration by rememberSaveable { mutableIntStateOf(dictionaryGeneration) }
    LaunchedEffect(dictionaryGeneration) {
        if (dictionaryGeneration == syncedGeneration) return@LaunchedEffect
        syncedGeneration = dictionaryGeneration
        studyViewModel.reload()
        recommendationViewModel.reload()
    }
    // 赞赏提示只在回到主标签时判定一次；划词弹窗走独立的 QuickTranslateActivity，不经过这里。
    val appContext = LocalContext.current.applicationContext
    val aboutStore = remember(appContext) { AboutStore(appContext) }
    val donationTipVisible by DonationPromptGate.visible.collectAsStateWithLifecycle()
    LaunchedEffect(selectedTab) { DonationPromptGate.evaluate(aboutStore) }
    val openDonation: () -> Unit = {
        DonationPromptGate.dismiss(aboutStore)
        aboutController.push(AboutScreenRoute.Donation)
    }
    val wideLayout = widthClass != WindowWidthSizeClass.Compact
    // 各标签共用播放状态，让发音按钮能同步显示播放或停止状态。
    val playingKey = (dictionaryState as? DictionaryScreenState.Ready)?.playingKey
    // 待复习数量角标：在词典/翻译/探索页也能看到今天还剩多少词要复习，不必先切回背词页确认。
    val pendingReviewCount = (studyState as? StudyScreenState.Ready)
        ?.takeIf { it.phase == StudyPhase.REVIEW }
        ?.reviewRemaining
        ?.takeIf { it > 0 }
    // 保留每个标签的滚动位置、输入内容和打开的详情，切回时不丢失上下文。
    val tabStateHolder = rememberSaveableStateHolder()

    // 内置词库内容变更后，提示用户重建本地数据库以加载新版释义。
    if (dictionaryState is DictionaryScreenState.Ready && dictionaryState.updateNeeded) {
        AlertDialog(
            onDismissRequest = onDictionaryDismissUpdate,
            icon = { Icon(imageVector = Icons.Filled.Autorenew, contentDescription = null) },
            title = { Text("检测到词典已更新") },
            text = {
                Text("本地词库需要重建以加载新版词典内容，通常只需几秒。重建期间暂不能查询，但不会影响背词进度。")
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
        // 各页面自行处理顶栏 inset；外壳仅提供导航栏占用的空间，避免重复补白。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                if (donationTipVisible) {
                    DonationTipBar(
                        onOpen = openDonation,
                        onDismiss = { DonationPromptGate.dismiss(aboutStore) },
                        // 侧边导航时底部没有导航栏兜住系统手势区，提示条自己补白。
                        modifier = if (useRail) {
                            Modifier.navigationBarsPadding().imePadding()
                        } else {
                            Modifier.imePadding()
                        },
                    )
                }
                if (!useRail) {
                    CdictNavigationBar(
                        selected = destination,
                        onSelect = { switchTab(it.ordinal) },
                        pendingReviewCount = pendingReviewCount,
                    )
                }
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
                    pendingReviewCount = pendingReviewCount,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding)
                        .then(if (needsStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
                ) {
                    DestinationContent(
                        destination = destination,
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
                        onToggleMastered = { word -> studyViewModel.toggleMastered(word.id) },
                        onOpenDictionaryWord = onOpenDictionaryWord,
                        playingKey = playingKey,
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
                    destination = destination,
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
                    onToggleMastered = { word -> studyViewModel.toggleMastered(word.id) },
                    onOpenDictionaryWord = onOpenDictionaryWord,
                    playingKey = playingKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DestinationContent(
    destination: CdictDestination,
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
    onOpenDictionaryWord: (WordEntity) -> Unit,
    playingKey: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = destination,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (
                fadeIn(animationSpec = tween(durationMillis = 180)) +
                    slideInHorizontally(
                        animationSpec = tween(durationMillis = 260),
                        initialOffsetX = { fullWidth -> fullWidth / 6 * direction },
                    )
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 150)) +
                        slideOutHorizontally(
                            animationSpec = tween(durationMillis = 220),
                            targetOffsetX = { fullWidth -> -fullWidth / 10 * direction },
                        ),
                    ).using(SizeTransform(clip = true))
        },
        label = "Destination transition",
    ) { target ->
        // 保留标签内状态，切换后可回到原来的滚动位置、输入内容与详情。
        stateHolder.SaveableStateProvider(key = target.name) {
        when (target) {
            CdictDestination.Study -> StudyScreen(
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
                onScopeChange = studyViewModel::onScopeChange,
                playingKey = playingKey,
            )
            CdictDestination.Dictionary -> DictionaryApp(
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
            CdictDestination.Translation -> TranslateScreen(translationViewModel)
            CdictDestination.Recommendation -> RecommendationScreen(
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
                onScopeChange = recommendationViewModel::onScopeChange,
                playingKey = playingKey,
            )
        }
        }
    }
}

@Composable
private fun CdictNavigationBar(
    selected: CdictDestination,
    onSelect: (CdictDestination) -> Unit,
    pendingReviewCount: Int?,
) {
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
                paneTitle = "CDict 主导航，当前为 ${selected.label}"
            },
        ) {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 0.dp,
            ) {
                CdictDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = dest == selected,
                        onClick = { onSelect(dest) },
                        icon = {
                            NavigationDestinationIcon(
                                destination = dest,
                                pendingReviewCount = pendingReviewCount,
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
    pendingReviewCount: Int?,
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
                    NavigationDestinationIcon(
                        destination = dest,
                        pendingReviewCount = pendingReviewCount,
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

/**
 * 导航项图标：背词标签在有待复习词时叠加数量角标（超过 99 显示 99+），
 * 让用户在任意标签都能看到今天剩余的复习量。角标带中文语义，读屏会朗读“待复习 N 个”。
 */
@Composable
private fun NavigationDestinationIcon(
    destination: CdictDestination,
    pendingReviewCount: Int?,
) {
    val count = pendingReviewCount?.takeIf { destination == CdictDestination.Study }
    if (count == null) {
        Icon(imageVector = destination.icon, contentDescription = null)
        return
    }
    BadgedBox(
        badge = {
            Badge(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "待复习 $count 个"
                },
            ) {
                Text(text = if (count > 99) "99+" else count.toString(), maxLines = 1)
            }
        },
    ) {
        Icon(imageVector = destination.icon, contentDescription = null)
    }
}