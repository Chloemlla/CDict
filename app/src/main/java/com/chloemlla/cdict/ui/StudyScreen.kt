package com.chloemlla.cdict.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

private val CorrectGreen = Color(0xFF2E7D32)
private val CorrectGreenContainer = Color(0xFFC8E6C9)
private val WrongRed = Color(0xFFC62828)
private val WrongRedContainer = Color(0xFFFFCDD2)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    state: StudyScreenState,
    onReload: () -> Unit,
    onAnswer: (Int) -> Unit,
    onAdvance: () -> Unit,
    onMarkLearned: () -> Unit,
    onDefer: () -> Unit,
    onContinueFreePlay: () -> Unit,
    onExitFreePlay: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("背词") },
                actions = {
                    IconButton(onClick = onReload, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新背词计划")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                StudyScreenState.Loading -> StudyLoading()
                is StudyScreenState.NoDictionary -> StudyError(state.message)
                is StudyScreenState.Ready -> when (state.phase) {
                    StudyPhase.REVIEW -> ReviewFlow(
                        state = state,
                        onAnswer = onAnswer,
                        onAdvance = onAdvance,
                    )
                    StudyPhase.LEARN, StudyPhase.FREE_PLAY -> LearnFlow(
                        state = state,
                        onMarkLearned = onMarkLearned,
                        onDefer = onDefer,
                        onContinueFreePlay = onContinueFreePlay,
                        onExitFreePlay = onExitFreePlay,
                        onSetGoal = onSetGoal,
                        onPlayPronunciation = onPlayPronunciation,
                    )
                    StudyPhase.DONE -> DoneFlow(
                        state = state,
                        onContinueFreePlay = onContinueFreePlay,
                        onSetGoal = onSetGoal,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在准备今日背词…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StudyError(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("无法打开词典", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---- Review flow ---------------------------------------------------------------------

@Composable
private fun ReviewFlow(
    state: StudyScreenState.Ready,
    onAnswer: (Int) -> Unit,
    onAdvance: () -> Unit,
) {
    val question = state.question
    if (question == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("复习题目准备中…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "昨日复习  ${state.reviewTotal - state.reviewRemaining + 1} / ${state.reviewTotal}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("选择正确的汉语释义", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = question.english,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                question.phonetic?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        question.options.forEachIndexed { index, option ->
            ReviewOption(
                index = index,
                text = option,
                enabled = state.feedback == null,
                highlight = highlightOf(state.feedback, option, question.correctText),
                onClick = { onAnswer(index) },
            )
        }
        Spacer(Modifier.weight(1f))
        val feedback = state.feedback
        when {
            feedback != null && feedback.correct -> {
                FeedbackBanner("回答正确，继续加油", CorrectGreen, CorrectGreenContainer)
                // Auto-advance to the next question after a brief green confirmation.
                LaunchedEffect(feedback) {
                    kotlinx.coroutines.delay(650)
                    onAdvance()
                }
            }
            feedback != null && !feedback.correct -> {
                FeedbackBanner("答错了，正确答案：${feedback.correctText}", WrongRed, WrongRedContainer)
                Button(
                    onClick = onAdvance,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text("继续 · 该词将末尾重试")
                }
            }
        }
    }
}

private fun highlightOf(
    feedback: ReviewFeedback?,
    optionText: String,
    correctText: String,
): ReviewOptionHighlight? {
    if (feedback == null) return null
    if (optionText == correctText) return ReviewOptionHighlight.Correct
    if (optionText == feedback.chosenText) return ReviewOptionHighlight.Wrong
    return null
}

private enum class ReviewOptionHighlight { Correct, Wrong }

@Composable
private fun ReviewOption(
    index: Int,
    text: String,
    enabled: Boolean,
    highlight: ReviewOptionHighlight?,
    onClick: () -> Unit,
) {
    val (bg, fg) = when (highlight) {
        ReviewOptionHighlight.Correct -> CorrectGreenContainer to Color(0xFF1B5E20)
        ReviewOptionHighlight.Wrong -> WrongRedContainer to WrongRed
        null -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${('A' + index)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeedbackBanner(text: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ---- Learning / free-flow ------------------------------------------------------------

@Composable
private fun LearnFlow(
    state: StudyScreenState.Ready,
    onMarkLearned: () -> Unit,
    onDefer: () -> Unit,
    onContinueFreePlay: () -> Unit,
    onExitFreePlay: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
) {
    val isFree = state.phase == StudyPhase.FREE_PLAY
    val card = state.card
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GoalStepper(goal = state.dailyGoal, onSetGoal = onSetGoal)
        StudyProgressBar(state)
        if (isFree) {
            Text(
                text = "自由刷词中 · 不计入今日进度与明日复习",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        card?.let { word ->
            LearnCard(word = word, onPlayPronunciation = onPlayPronunciation)
        } ?: Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text("正在为你挑选新词…", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDefer,
                enabled = card != null,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text("稍后再看")
            }
            Button(
                onClick = onMarkLearned,
                enabled = card != null,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text(if (isFree) "刷完" else "我已背会")
            }
        }
        if (isFree) {
            FilledTonalButton(
                onClick = onExitFreePlay,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("返回今日总结")
            }
        }
    }
}

@Composable
private fun LearnCard(
    word: WordEntity,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
) {
    val pos = word.translation?.takeIf(String::isNotBlank)?.let { primaryPartOfSpeech(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            pos?.takeIf { it.isNotBlank() }?.let {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(partOfSpeechLabel(it), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            val phonetics = listOfNotNull(
                word.phoneticUk?.takeIf(String::isNotBlank)?.let { "英  $it" },
                word.phoneticUs?.takeIf(String::isNotBlank)?.let { "美  $it" },
            )
            if (phonetics.isNotEmpty()) {
                Text(
                    text = phonetics.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onPlayPronunciation(word, Accent.UK) }, contentPadding = ButtonDefaults.ContentPadding) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("英音")
                }
                FilledTonalButton(onClick = { onPlayPronunciation(word, Accent.US) }, contentPadding = ButtonDefaults.ContentPadding) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("美音")
                }
            }
        }
    }
}

private fun partOfSpeechLabel(pos: String): String = when (pos) {
    "n" -> "名词"
    "v" -> "动词"
    "adj" -> "形容词"
    "adv" -> "副词"
    "prep" -> "介词"
    "conj" -> "连词"
    "pron" -> "代词"
    "num" -> "数词"
    "art" -> "冠词"
    else -> pos
}

@Composable
private fun StudyProgressBar(state: StudyScreenState.Ready) {
    val fraction = if (state.dailyGoal > 0) state.todayDone.toFloat() / state.dailyGoal else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("今日进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${state.todayDone} / ${state.dailyGoal}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}

@Composable
private fun GoalStepper(goal: Int, onSetGoal: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = { if (goal > DAILY_GOAL_MIN) onSetGoal(goal - DAILY_GOAL_STEP) }) {
            Icon(Icons.Filled.Remove, contentDescription = "减少每日背词量")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("每日背词量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { if (goal < DAILY_GOAL_MAX) onSetGoal(goal + DAILY_GOAL_STEP) }) {
            Icon(Icons.Filled.Add, contentDescription = "增加每日背词量")
        }
    }
}

// ---- Done / summary ------------------------------------------------------------------

@Composable
private fun DoneFlow(
    state: StudyScreenState.Ready,
    onContinueFreePlay: () -> Unit,
    onSetGoal: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            GoalStepper(goal = state.dailyGoal, onSetGoal = onSetGoal)
            StudyProgressBar(state)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("今日学习已达标", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = "已背会 ${state.todayDone} 个单词，明天将安排复习。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onContinueFreePlay,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
                    ) {
                        Text("继续自由刷词（不计入明日复习）")
                    }
                }
            }
        }
        if (state.learnedToday.isNotEmpty()) {
            item(key = "learned-title") {
                Text("今日已学清单 · ${state.learnedToday.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
            items(state.learnedToday, key = { it.id }) { word ->
                LearnedWordRow(word)
            }
        }
    }
}

@Composable
private fun LearnedWordRow(word: WordEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            word.translation?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}