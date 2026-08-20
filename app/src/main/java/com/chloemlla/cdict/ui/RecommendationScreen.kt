package com.chloemlla.cdict.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.RecommendationPool
import com.chloemlla.cdict.core.data.WordEntity

/**
 * 推荐页用于每日探索与发现。正文按 5:3:2 混排核心新词、派生拓展和高频过渡卡片与后续队列；
 * 推荐页只负责“输入 / 预热”，复习权交还背词页，不再混入复习巩固。中等及以上宽度使用左侧
 * 大卡、右侧队列的两栏布局，紧凑宽度使用纵向滚动单列。英文释义复用应用的自动翻译管线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    state: RecommendationScreenState,
    onReload: () -> Unit,
    onMarkLearned: () -> Unit,
    onMarkMastered: () -> Unit,
    onDefer: () -> Unit,
    onContinueMore: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onOpenWord: (WordEntity) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    wideLayout: Boolean,
    onScopeChange: (StudyScope) -> Unit = {},
    playingKey: String? = null,
) {
    val context = LocalContext.current
    val phraseViewModel: PhraseSpeechViewModel = viewModel(
        factory = remember { PhraseSpeechViewModelFactory(context) },
    )
    val phraseStates by phraseViewModel.states.collectAsStateWithLifecycle()
    val speakingKey by phraseViewModel.speakingKey.collectAsStateWithLifecycle()
    val onTranslate = phraseViewModel::translate
    val onSpeak = phraseViewModel::speak

    // 词池筛选状态在重组期间保持不变。
    val poolFilter = remember { mutableStateOf<RecommendationPool?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Recommend,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                "探索",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "每日探索 · 5:3:2 配比",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onReload, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新生成今日推荐")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        ResponsiveContentBox(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RecommendationScreenState.Loading -> RecommendationLoading()
                RecommendationScreenState.NoDictionary -> RecommendationNoDictionary(onReload)
                is RecommendationScreenState.Ready -> {
                    if (state.items.isEmpty()) {
                        RecommendationEmpty(
                            state = state,
                            onContinueMore = onContinueMore,
                            onReload = onReload,
                            onSetGoal = onSetGoal,
                            onScopeChange = onScopeChange,
                        )
                    } else if (wideLayout) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(3f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // 宽屏同样显示目标 / 进度 / 图例，并让当前卡在低高度窗口下可滚动。
                                RecommendationHeader(
                                    state = state,
                                    onSetGoal = onSetGoal,
                                    onScopeChange = onScopeChange,
                                    onFilterPool = { poolFilter.value = it },
                                    currentPoolFilter = poolFilter.value,
                                )
                                // 当前卡永远是队列真实头部：底部操作走 ViewModel 的队首，
                                // 若这里改用筛选后的头部，操作的词会与卡面显示的词不一致。
                                RecommendationCurrentCard(
                                    state = state,
                                    phraseStates = phraseStates,
                                    onTranslate = onTranslate,
                                    onSpeak = onSpeak,
                                    onMarkLearned = onMarkLearned,
                                    onMarkMastered = onMarkMastered,
                                    onDefer = onDefer,
                                    onOpenWord = onOpenWord,
                                    onPlayPronunciation = onPlayPronunciation,
                                    playingKey = playingKey,
                                    speakingKey = speakingKey,
                                )
                            }
                            // 图例筛选只作用于「接下来」预览队列（只读，点击进词典详情）。
                            RecommendationUpcomingList(
                                upcoming = state.items.drop(1)
                                    .filter { poolFilter.value == null || it.pool == poolFilter.value },
                                filtered = poolFilter.value != null,
                                onClearFilter = { poolFilter.value = null },
                                onOpenWord = onOpenWord,
                                modifier = Modifier.weight(2f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RecommendationHeader(
                                state = state,
                                onSetGoal = onSetGoal,
                                onScopeChange = onScopeChange,
                                onFilterPool = { poolFilter.value = it },
                                currentPoolFilter = poolFilter.value,
                            )
                            // 当前卡永远是队列真实头部（同宽屏分支的理由）。
                            RecommendationCurrentCard(
                                state = state,
                                phraseStates = phraseStates,
                                onTranslate = onTranslate,
                                onSpeak = onSpeak,
                                onMarkLearned = onMarkLearned,
                                onMarkMastered = onMarkMastered,
                                onDefer = onDefer,
                                onOpenWord = onOpenWord,
                                onPlayPronunciation = onPlayPronunciation,
                                playingKey = playingKey,
                                speakingKey = speakingKey,
                            )
                            RecommendationUpcomingBlock(
                                upcoming = state.items.drop(1)
                                    .filter { poolFilter.value == null || it.pool == poolFilter.value },
                                filtered = poolFilter.value != null,
                                onClearFilter = { poolFilter.value = null },
                                onOpenWord = onOpenWord,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationLoading() {
    val transition = rememberInfiniteTransition(label = "rec-loading-shimmer")
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-loading-pulse",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .alpha(pulse)
                    .semantics {
                        contentDescription = "正在生成今日推荐"
                    },
            )
            Text(
                text = "正在生成今日推荐…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(pulse),
            )
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f - i * 0.1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = pulse))
                        .alpha(0.5f),
                )
            }
        }
    }
}

@Composable
private fun RecommendationNoDictionary(onReload: () -> Unit) {
    // 错误卡片约 300dp 高：横屏等低窗口高度下必须可滚动，否则标题与「重试加载」会被居中裁掉且无法触达。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                SelectionContainer {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "无法打开离线词典",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = "推荐页依赖本地词库，请确认安装包包含 dict.db。",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Button(
                    onClick = onReload,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重试加载")
                }
            }
        }
    }
}

@Composable
private fun RecommendationHeader(
    state: RecommendationScreenState.Ready,
    onSetGoal: (Int) -> Unit,
    onScopeChange: (StudyScope) -> Unit = {},
    onFilterPool: (RecommendationPool?) -> Unit = {},
    currentPoolFilter: RecommendationPool? = null,
    showPoolLegend: Boolean = true,
) {
    val rawFraction = if (state.dailyGoal > 0) state.handledToday.toFloat() / state.dailyGoal else 0f
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250),
        label = "progress-fraction",
    )
    val isComplete = state.handledToday >= state.dailyGoal && state.dailyGoal > 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeFilterRow(
            scope = state.scope,
            availableCurriculumTags = state.availableCurriculumTags,
            onScopeChange = onScopeChange,
        )
        RecommendationGoalStepper(goal = state.dailyGoal, onSetGoal = onSetGoal)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "今日已完成",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${state.handledToday} / ${state.dailyGoal}",
                style = MaterialTheme.typography.labelMedium,
                color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics {
                    contentDescription = "今日学习目标完成进度（与背词页共用）"
                    stateDescription = "已完成 ${state.handledToday} 个，共 ${state.dailyGoal} 个"
                },
        )
        if (isComplete) {
            Text(
                "今日目标已达成！",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        // 空队列时图例是「死按钮」（筛选只作用于预览队列），此时由调用方隐藏。
        if (showPoolLegend) {
            RecommendationPoolLegend(
                currentPoolFilter = currentPoolFilter,
                onFilterPool = onFilterPool,
            )
        }
    }
}

/** 预览队列的类别图例 + 当前筛选提示 + 一键清除。 */
@Composable
private fun RecommendationPoolLegend(
    currentPoolFilter: RecommendationPool?,
    onFilterPool: (RecommendationPool?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "预览类别",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (currentPoolFilter == null) "显示全部类别" else "仅显示${recommendationPoolLabel(currentPoolFilter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (currentPoolFilter != null) {
                TextButton(
                    onClick = { onFilterPool(null) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("清除筛选")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecommendationLegendPill(
                text = "核心 50%",
                pool = RecommendationPool.CORE_NEW,
                isSelected = currentPoolFilter == RecommendationPool.CORE_NEW,
                filterActive = currentPoolFilter != null,
                modifier = Modifier.weight(1f),
                onClick = {
                    onFilterPool(
                        if (currentPoolFilter == RecommendationPool.CORE_NEW) null else RecommendationPool.CORE_NEW,
                    )
                },
            )
            RecommendationLegendPill(
                text = "派生 30%",
                pool = RecommendationPool.EXPANSION,
                isSelected = currentPoolFilter == RecommendationPool.EXPANSION,
                filterActive = currentPoolFilter != null,
                modifier = Modifier.weight(1f),
                onClick = {
                    onFilterPool(
                        if (currentPoolFilter == RecommendationPool.EXPANSION) null else RecommendationPool.EXPANSION,
                    )
                },
            )
            RecommendationLegendPill(
                text = "过渡 20%",
                pool = RecommendationPool.SIMPLE,
                isSelected = currentPoolFilter == RecommendationPool.SIMPLE,
                filterActive = currentPoolFilter != null,
                modifier = Modifier.weight(1f),
                onClick = {
                    onFilterPool(
                        if (currentPoolFilter == RecommendationPool.SIMPLE) null else RecommendationPool.SIMPLE,
                    )
                },
            )
        }
    }
}

@Composable
private fun RecommendationCurrentCard(
    state: RecommendationScreenState.Ready,
    phraseStates: Map<String, PhraseUiState>,
    onTranslate: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onMarkLearned: () -> Unit,
    onMarkMastered: () -> Unit,
    onDefer: () -> Unit,
    onOpenWord: (WordEntity) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    playingKey: String? = null,
    speakingKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val head = state.items.first()
    val word = head.word
    val haptic = LocalHapticFeedback.current
    var showMasteredConfirmation by remember(word.id) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            @Suppress("UnusedContentLambdaTargetStateParameter")
            AnimatedContent(
                targetState = word.id,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(220)) { -it / 4 } + fadeOut(tween(180)),
                        )
                },
                label = "recommendation-card",
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "当前推荐",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { heading() },
                        )
                        RecommendationModePill(head.pool)
                    }
                    WordCardContent(
                        word = word,
                        phraseStates = phraseStates,
                        onPlayPronunciation = onPlayPronunciation,
                        onTranslate = onTranslate,
                        onSpeak = onSpeak,
                        playingKey = playingKey,
                        speakingKey = speakingKey,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        showPartOfSpeech = true,
                        bottomContent = {
                            RecommendationWordMetadata(word)
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PressableOutlinedButton(
                    onClick = onDefer,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .semantics { contentDescription = "稍后再看 ${word.word}" },
                ) {
                    Text("稍后再看")
                }
                PressableButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMarkLearned()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .semantics { contentDescription = "把 ${word.word} 纳入复习计划，明天开始测验" },
                ) {
                    Text("纳入复习计划")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { showMasteredConfirmation = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = "将 ${word.word} 标记为已掌握，需要二次确认"
                        },
                ) {
                    Text("已掌握")
                }
                TextButton(
                    onClick = { onOpenWord(word) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "查看 ${word.word} 的完整词条" },
                ) {
                    Text("查看完整词条")
                }
            }
        }
    }
    if (showMasteredConfirmation) {
        AlertDialog(
            onDismissRequest = { showMasteredConfirmation = false },
            title = { Text("确认标记为已掌握") },
            text = {
                Text("“${word.word}”将从推荐队列移除，且不会进入复习计划。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMasteredConfirmation = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMarkMastered()
                    },
                ) {
                    Text("确认已掌握")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMasteredConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 「接下来」队列预览。[upcoming] 由调用方算好（已去掉当前卡、并应用图例筛选），
 * 这样筛选只影响预览列表，不会改变当前卡——当前卡必须始终是队列真实头部，
 * 否则底部操作按钮（纳入复习计划 / 已掌握 / 稍后再看）作用的词与卡面显示的词会不一致。
 */
@Composable
private fun RecommendationUpcomingBlock(
    upcoming: List<RecommendationItemCard>,
    filtered: Boolean,
    onClearFilter: () -> Unit,
    onOpenWord: (WordEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "接下来 · ${upcoming.size}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 4.dp)
                .semantics { heading() },
        )
        if (upcoming.isEmpty()) {
            Text(
                text = if (filtered) "该类别下没有后续推荐。" else "这是今天的最后一个推荐。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filtered) {
                OutlinedButton(
                    onClick = onClearFilter,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("显示全部类别")
                }
            }
        } else {
            upcoming.forEach { card -> RecommendationUpcomingRow(card, onOpenWord) }
        }
    }
}

@Composable
private fun RecommendationUpcomingList(
    upcoming: List<RecommendationItemCard>,
    filtered: Boolean,
    onClearFilter: () -> Unit,
    onOpenWord: (WordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "upcoming-header") {
                Text(
                    "接下来 · ${upcoming.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (upcoming.isEmpty()) {
                item(key = "upcoming-empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (filtered) "该类别下没有后续推荐。" else "这是今天的最后一个推荐。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (filtered) {
                            OutlinedButton(
                                onClick = onClearFilter,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text("显示全部类别")
                            }
                        }
                    }
                }
            } else {
                items(upcoming, key = { it.word.id }) { card ->
                    RecommendationUpcomingRow(card, onOpenWord)
                }
            }
        }
    }
}

@Composable
private fun RecommendationUpcomingRow(
    card: RecommendationItemCard,
    onOpenWord: (WordEntity) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "upcoming-row-press-scale",
    )
    val rowShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(rowShape)
            .clickable(
                interactionSource = interactionSource,
                onClickLabel = "查看单词 ${card.word.word}",
                role = Role.Button,
                onClick = { onOpenWord(card.word) },
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = rowShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecommendationPoolDot(card.pool)
            Column(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    Column {
                        Text(
                            text = card.word.word,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        card.word.translation?.takeIf(String::isNotBlank)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationEmpty(
    state: RecommendationScreenState.Ready,
    onContinueMore: () -> Unit,
    onReload: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onScopeChange: (StudyScope) -> Unit = {},
) {
    val scopeFiltered = state.scope.curriculumTag != null || state.scope.frequencyGroup != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecommendationHeader(
            state = state,
            onSetGoal = onSetGoal,
            onScopeChange = onScopeChange,
            showPoolLegend = false,
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "今日推荐已学完",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (scopeFiltered) {
                        "已处理 ${state.handledToday} 个词。当前范围内没有更多新词，可再补一批、重新生成，或放宽范围。"
                    } else {
                        "已处理 ${state.handledToday} 个词。可再补一批，或重新生成今日推荐。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onContinueMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(top = 8.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("再来一批")
                }
                // OutlinedButton 默认用 primary 作前景，压在 primaryContainer 上对比度不足，这里改用 onPrimaryContainer。
                OutlinedButton(
                    onClick = onReload,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainer),
                    border = BorderStroke(1.dp, onContainer.copy(alpha = 0.5f)),
                ) {
                    Text("重新生成今日推荐")
                }
                if (scopeFiltered) {
                    OutlinedButton(
                        onClick = { onScopeChange(StudyScope()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainer),
                        border = BorderStroke(1.dp, onContainer.copy(alpha = 0.5f)),
                    ) {
                        Text("放宽范围：全部词表与全部组")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationGoalStepper(goal: Int, onSetGoal: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = { onSetGoal(goal - DAILY_GOAL_STEP) },
            enabled = goal > DAILY_GOAL_MIN,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少每日推荐量")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "每日推荐目标"
                    stateDescription = "$goal 个"
                },
        ) {
            Text("每日目标", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        IconButton(
            onClick = { onSetGoal(goal + DAILY_GOAL_STEP) },
            enabled = goal < DAILY_GOAL_MAX,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加每日推荐量")
        }
    }
}

@Composable
private fun RecommendationModePill(pool: RecommendationPool) {
    val (targetBg, targetFg) = poolColors(pool)
    val bg by animateColorAsState(targetValue = targetBg, animationSpec = tween(250), label = "mode-pill-bg")
    val fg by animateColorAsState(targetValue = targetFg, animationSpec = tween(250), label = "mode-pill-fg")
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = recommendationPoolLabel(pool),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun RecommendationLegendPill(
    text: String,
    pool: RecommendationPool,
    isSelected: Boolean,
    filterActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (targetBg, targetFg) = poolColors(pool)
    val bg by animateColorAsState(targetValue = targetBg, animationSpec = tween(250), label = "legend-pill-bg")
    val fg by animateColorAsState(targetValue = targetFg, animationSpec = tween(250), label = "legend-pill-fg")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "legend-pill-scale",
    )
    val pillShape = RoundedCornerShape(8.dp)
    // 胶囊上只显示「核心 50%」这类短文案，读屏改用完整类别名，避免读出百分比噪声。
    val poolName = recommendationPoolLabel(pool)
    Surface(
        color = if (!filterActive || isSelected) bg else bg.copy(alpha = 0.4f),
        contentColor = if (!filterActive || isSelected) fg else fg.copy(alpha = 0.7f),
        shape = pillShape,
        border = if (isSelected) BorderStroke(1.5.dp, fg.copy(alpha = 0.5f)) else null,
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(pillShape)
            .clickable(
                interactionSource = interactionSource,
                onClickLabel = if (isSelected) "取消只看${poolName}" else "只看$poolName",
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = "预览类别筛选：$poolName"
                stateDescription = when {
                    isSelected -> "已筛选，点击取消"
                    filterActive -> "未筛选，点击改为只看该类别"
                    else -> "正在显示，点击只看该类别"
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecommendationPoolDot(pool: RecommendationPool) {
    val (targetBg, _) = poolColors(pool)
    val bg by animateColorAsState(targetValue = targetBg, animationSpec = tween(250), label = "pool-dot-bg")
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(10.dp),
    ) {}
}

@Composable
private fun RecommendationWordMetadata(word: WordEntity) {
    val source = parseCurriculumTags(word.curriculumTags).firstOrNull()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (word.frequency > 0 || word.frequencyGroup > 0) {
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (word.frequency > 0) {
                    RecommendationInfoPill("IELTS 频率 ${word.frequency}")
                }
                if (word.frequencyGroup > 0) {
                    RecommendationInfoPill("频率组 ${word.frequencyGroup}")
                }
            }
        }
        source?.let {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "词表 · $it",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RecommendationInfoPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun poolColors(pool: RecommendationPool): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (pool) {
        RecommendationPool.CORE_NEW ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RecommendationPool.EXPANSION ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        RecommendationPool.SIMPLE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

@Composable
private fun PressableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "button-press-scale",
    )
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
    ) {
        content()
    }
}

@Composable
private fun PressableOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "outlined-press-scale",
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interactionSource,
    ) {
        content()
    }
}