package com.chloemlla.cdict.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
 * 推荐页（今日推荐流）。顶部是独有的推荐图标 TopAppBar；正文按 3:5:2 混排的核心卡片与
 * 后续队列。宽的布局（medium/expanded）左侧大卡 + 右侧队列两栏，窄布局（compact）纵向
 * 滚动单列。英文释义经 vivo 网关自动翻译为中文（复用词典 / 背词页的非中文自动翻译管线）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    state: RecommendationScreenState,
    onReload: () -> Unit,
    onMarkLearned: () -> Unit,
    onDefer: () -> Unit,
    onContinueMore: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onOpenWord: (WordEntity) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    wideLayout: Boolean,
) {
    val context = LocalContext.current
    val phraseViewModel: PhraseSpeechViewModel = viewModel(
        factory = remember { PhraseSpeechViewModelFactory(context) },
    )
    val phraseStates by phraseViewModel.states.collectAsStateWithLifecycle()
    val onTranslate = phraseViewModel::translate
    val onSpeak = phraseViewModel::speak

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
                        Column {
                            Text("推荐", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "今日推荐 · 3:5:2 记忆配比",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                RecommendationScreenState.NoDictionary -> RecommendationNoDictionary()
                is RecommendationScreenState.Ready -> {
                    if (state.items.isEmpty()) {
                        RecommendationEmpty(
                            state = state,
                            onContinueMore = onContinueMore,
                            onSetGoal = onSetGoal,
                        )
                    } else if (wideLayout) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RecommendationCurrentCard(
                                state = state,
                                phraseStates = phraseStates,
                                onTranslate = onTranslate,
                                onSpeak = onSpeak,
                                onMarkLearned = onMarkLearned,
                                onDefer = onDefer,
                                onOpenWord = onOpenWord,
                                onPlayPronunciation = onPlayPronunciation,
                                modifier = Modifier.weight(3f),
                            )
                            RecommendationUpcomingList(
                                state = state,
                                onOpenWord = onOpenWord,
                                modifier = Modifier.weight(2f),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RecommendationHeader(state, onSetGoal)
                            RecommendationCurrentCard(
                                state = state,
                                phraseStates = phraseStates,
                                onTranslate = onTranslate,
                                onSpeak = onSpeak,
                                onMarkLearned = onMarkLearned,
                                onDefer = onDefer,
                                onOpenWord = onOpenWord,
                                onPlayPronunciation = onPlayPronunciation,
                            )
                            RecommendationUpcomingBlock(state, onOpenWord)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("正在生成今日推荐…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RecommendationNoDictionary() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "无法打开离线词典",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "推荐页依赖本地词库，请确认安装包包含 dict.db。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecommendationHeader(
    state: RecommendationScreenState.Ready,
    onSetGoal: (Int) -> Unit,
) {
    val fraction = if (state.dailyGoal > 0) state.handledToday.toFloat() / state.dailyGoal else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RecommendationGoalStepper(goal = state.dailyGoal, onSetGoal = onSetGoal)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "今日已推荐",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${state.handledToday} / ${state.dailyGoal}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecommendationLegendPill("复习 30%", RecommendationPool.REVIEW)
            RecommendationLegendPill("新词 50%", RecommendationPool.CORE_NEW)
            RecommendationLegendPill("简单 20%", RecommendationPool.SIMPLE)
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
    onDefer: () -> Unit,
    onOpenWord: (WordEntity) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val head = state.items.first()
    val word = head.word
    val phonetics = listOfNotNull(
        word.phoneticUk?.takeIf(String::isNotBlank)?.let { "英  $it" },
        word.phoneticUs?.takeIf(String::isNotBlank)?.let { "美  $it" },
    )
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
            RecommendationModePill(head.pool)
            Text(
                text = word.word,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (phonetics.isNotEmpty()) {
                Text(
                    text = phonetics.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            word.translation?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            // 非中文（英文释义）自动翻译为中文，与词典 / 背词页一致。
            word.definition?.takeIf(String::isNotBlank)?.let { def ->
                SpeakableEnglishText(
                    en = def,
                    pinnedZh = null,
                    ui = phraseStates[def],
                    onTranslate = onTranslate,
                    onSpeak = onSpeak,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecommendationInfoPill("IELTS 频率 ${word.frequency}")
                RecommendationInfoPill("组 ${word.frequencyGroup}")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { onPlayPronunciation(word, Accent.UK) },
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("英音")
                }
                FilledTonalButton(
                    onClick = { onPlayPronunciation(word, Accent.US) },
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("美音")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDefer,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("稍后再看")
                }
                Button(
                    onClick = onMarkLearned,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("我已背会")
                }
            }
            TextButton(onClick = { onOpenWord(word) }) {
                Text("查看详情")
            }
        }
    }
}

@Composable
private fun RecommendationUpcomingBlock(
    state: RecommendationScreenState.Ready,
    onOpenWord: (WordEntity) -> Unit,
) {
    val upcoming = state.items.drop(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "接下来 · ${upcoming.size}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (upcoming.isEmpty()) {
            Text(
                "今天的推荐已全部看完。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            upcoming.forEach { card -> RecommendationUpcomingRow(card, onOpenWord) }
        }
    }
}

@Composable
private fun RecommendationUpcomingList(
    state: RecommendationScreenState.Ready,
    onOpenWord: (WordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val upcoming = state.items.drop(1)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "upcoming-header") {
            Text(
                "接下来 · ${upcoming.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (upcoming.isEmpty()) {
            item(key = "upcoming-empty") {
                Text(
                    "今天的推荐已全部看完。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(upcoming, key = { it.word.id }) { card ->
                RecommendationUpcomingRow(card, onOpenWord)
            }
        }
    }
}

@Composable
private fun RecommendationUpcomingRow(
    card: RecommendationItemCard,
    onOpenWord: (WordEntity) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "查看单词 ${card.word.word}",
                role = Role.Button,
                onClick = { onOpenWord(card.word) },
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RecommendationPoolDot(card.pool)
            Column(modifier = Modifier.weight(1f)) {
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

@Composable
private fun RecommendationEmpty(
    state: RecommendationScreenState.Ready,
    onContinueMore: () -> Unit,
    onSetGoal: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecommendationHeader(state, onSetGoal)
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "今日推荐已学完",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "已处理 ${state.handledToday} 个词。可再补一批，或继续背词页复习。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
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
        IconButton(onClick = { if (goal > DAILY_GOAL_MIN) onSetGoal(goal - DAILY_GOAL_STEP) }) {
            Icon(Icons.Filled.Remove, contentDescription = "减少每日推荐量")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text("每日目标", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { if (goal < DAILY_GOAL_MAX) onSetGoal(goal + DAILY_GOAL_STEP) }) {
            Icon(Icons.Filled.Add, contentDescription = "增加每日推荐量")
        }
    }
}

@Composable
private fun RecommendationModePill(pool: RecommendationPool) {
    val (bg, fg) = poolColors(pool)
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
private fun RecommendationLegendPill(text: String, pool: RecommendationPool) {
    val (bg, fg) = poolColors(pool)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RecommendationPoolDot(pool: RecommendationPool) {
    val (bg, _) = poolColors(pool)
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(10.dp),
    ) {}
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
        RecommendationPool.REVIEW ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        RecommendationPool.CORE_NEW ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        RecommendationPool.SIMPLE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }