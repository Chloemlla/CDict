package com.chloemlla.cdict.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

/** 五次点击标题后解锁开发者测试面板。 */
private const val DEVELOPER_KEY = "Chloemlla"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    state: StudyScreenState,
    onReload: () -> Unit,
    onAnswer: (Int) -> Unit,
    onAdvance: () -> Unit,
    onQuestionPresented: () -> Unit,
    onDebugLaunchReview: () -> Unit,
    onStartImmediateTest: () -> Unit,
    onMarkLearned: () -> Unit,
    onDefer: () -> Unit,
    onContinueFreePlay: () -> Unit,
    onExitFreePlay: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    onScopeChange: (StudyScope) -> Unit = {},
    playingKey: String? = null,
) {
    // 与词典词条一致：背词卡片的英文释义也经 vivo 网关自动翻译为中文，翻译状态按文本缓存。
    val context = LocalContext.current
    val phraseViewModel: PhraseSpeechViewModel = viewModel(
        factory = remember { PhraseSpeechViewModelFactory(context) },
    )
    val phraseStates by phraseViewModel.states.collectAsStateWithLifecycle()
    val speakingKey by phraseViewModel.speakingKey.collectAsStateWithLifecycle()
    // 连点标题五次仅用于开发测试，避免干扰正常学习流程。
    var devTaps by remember { mutableIntStateOf(0) }
    var lastDevTap by remember { mutableLongStateOf(0L) }
    var showDevDialog by remember { mutableStateOf(false) }
    var devKey by remember { mutableStateOf("") }
    var devKeyWrong by remember { mutableStateOf(false) }
    var devUnlocked by remember { mutableStateOf(false) }
    fun onTitleTap() {
        val now = SystemClock.uptimeMillis()
        devTaps = if (now - lastDevTap < 2000L) devTaps + 1 else 1
        lastDevTap = now
        if (devTaps >= 5) {
            devTaps = 0
            showDevDialog = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .pointerInput(Unit) { detectTapGestures { onTitleTap() } }
                            .semantics { heading() },
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Column {
                            Text("背词", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "IELTS 背词",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
                is StudyScreenState.NoDictionary -> StudyError(state.message, onReload)
                is StudyScreenState.Ready -> when (state.phase) {
                    StudyPhase.REVIEW -> ReviewFlow(
                        state = state,
                        onAnswer = onAnswer,
                        onAdvance = onAdvance,
                        onQuestionPresented = onQuestionPresented,
                    )
                    StudyPhase.LEARN, StudyPhase.FREE_PLAY -> LearnFlow(
                        state = state,
                        onMarkLearned = onMarkLearned,
                        onDefer = onDefer,
                        onStartImmediateTest = onStartImmediateTest,
                        onContinueFreePlay = onContinueFreePlay,
                        onExitFreePlay = onExitFreePlay,
                        onSetGoal = onSetGoal,
                        onPlayPronunciation = onPlayPronunciation,
                        phraseStates = phraseStates,
                        onTranslate = phraseViewModel::translate,
                        onSpeak = phraseViewModel::speak,
                        speakingKey = speakingKey,
                        onScopeChange = onScopeChange,
                        playingKey = playingKey,
                        onReload = onReload,
                    )
                    StudyPhase.DONE -> DoneFlow(
                        state = state,
                        onStartImmediateTest = onStartImmediateTest,
                        onContinueFreePlay = onContinueFreePlay,
                        onSetGoal = onSetGoal,
                        onScopeChange = onScopeChange,
                    )
                }
            }
        }
    }
    if (showDevDialog) {
        AlertDialog(
            onDismissRequest = { showDevDialog = false },
            title = { Text("开发者模式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (devUnlocked) {
                        Text(
                            "开发者已解锁。点击下方按钮直接进入「复习单词考试」界面，仅供开发测试，不改动真实学习数据。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                showDevDialog = false
                                onDebugLaunchReview()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("测试 复习单词考试页")
                        }
                    } else {
                        Text(
                            "请输入开发者密钥以解锁开发者模式。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = devKey,
                            onValueChange = {
                                devKey = it
                                devKeyWrong = false
                            },
                            singleLine = true,
                            label = { Text("开发者密钥") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = devKeyWrong,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (devKeyWrong) {
                            Text("密钥错误", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                if (devKey == DEVELOPER_KEY) {
                                    devUnlocked = true
                                    devKeyWrong = false
                                } else {
                                    devKeyWrong = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("解锁")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevDialog = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun StudyLoading() {
    val transition = rememberInfiniteTransition(label = "study-loading-shimmer")
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "study-loading-pulse",
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(
                modifier = Modifier
                    .alpha(pulse)
                    .semantics {
                        contentDescription = "正在准备今日背词"
                    },
            )
            Text(
                text = "正在准备今日背词…",
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
private fun StudyError(message: String, onReload: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "无法打开词典",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message.takeIf { it.isNotBlank() } ?: "无法读取本地词典数据。",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
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
private fun StudyEmpty(
    onReload: () -> Unit,
    onScopeChange: (StudyScope) -> Unit,
    availableCurriculumTags: List<String>,
    currentScope: StudyScope,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                )
                Text(
                    text = "暂无可学习词条",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "当前筛选条件下没有可学习的词条。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // 保留筛选入口，让用户可直接放宽范围继续学习。
                if (availableCurriculumTags.isNotEmpty()) {
                    ScopeFilterRow(
                        scope = currentScope,
                        availableCurriculumTags = availableCurriculumTags,
                        onScopeChange = onScopeChange,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onScopeChange(StudyScope()) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("查看全部")
                    }
                    Button(
                        onClick = onReload,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新")
                    }
                }
            }
        }
    }
}

// ---- 复习流程 ---------------------------------------------------------------------

@Composable
private fun ReviewFlow(
    state: StudyScreenState.Ready,
    onAnswer: (Int) -> Unit,
    onAdvance: () -> Unit,
    onQuestionPresented: () -> Unit,
) {
    val question = state.question
    if (question == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("复习题目准备中…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    // 每次进入新题、重试或释义展示完毕后重置犹豫计时，供错因归因使用。
    LaunchedEffect(question.wordId, question.attempt) { onQuestionPresented() }
    // 完全陌生的重试先展示释义，再显示作答选项。
    var reveal by remember(question.wordId, question.attempt, question.forceReveal) {
        mutableStateOf(!question.forceReveal)
    }
    LaunchedEffect(question.wordId, question.attempt, question.forceReveal) {
        if (question.forceReveal) {
            kotlinx.coroutines.delay(1500)
            reveal = true
            onQuestionPresented()
        }
    }
    // 提示音随复习界面释放，避免离开复习后仍占用音频资源。
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val tone = remember(context) { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }
    DisposableEffect(tone) {
        onDispose { tone.release() }
    }
    // 手机小屏：整列可滚动避免裁剪；换题时回到顶部。
    val scrollState = rememberScrollState()
    LaunchedEffect(question.wordId, question.attempt, question.forceReveal) {
        scrollState.scrollTo(0)
    }
    ResponsiveContentBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        val header = if (state.isImmediateTest) "今日测试" else "昨日复习"
        val currentNumber = (state.reviewTotal - state.reviewRemaining + 1)
            .coerceIn(1, state.reviewTotal.coerceAtLeast(1))
        Text(
            text = "$header  $currentNumber / ${state.reviewTotal}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$header，第 $currentNumber 题，共 ${state.reviewTotal} 题，剩余 ${state.reviewRemaining} 题"
                    liveRegion = LiveRegionMode.Polite
                },
            textAlign = TextAlign.Center,
        )
        LinearProgressIndicator(
            progress = {
                if (state.reviewTotal > 0) currentNumber.toFloat() / state.reviewTotal else 0f
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .semantics {
                    contentDescription = "复习进度"
                    stateDescription = "已完成 $currentNumber 题，共 ${state.reviewTotal} 题"
                },
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (question.forceReveal && !reveal) "请先记忆释义" else "选择正确的汉语释义",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = question.english,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .semantics {
                            heading()
                            contentDescription = "题目：${question.english}"
                        },
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
        if (question.forceReveal && !reveal) {
            // 识记卡片：陌生词的释义先亮出，再进入作答。
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = question.correctText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            return@Column
        }
        if (question.confusionRetry) {
            Text(
                text = "存在易混淆干扰项，请仔细辨析",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        question.options.forEachIndexed { index, option ->
            ReviewOption(
                index = index,
                text = option,
                enabled = state.feedback == null,
                highlight = highlightOf(state.feedback, option, question.correctText),
                isSelected = state.feedback?.chosenText == option,
                onClick = { onAnswer(index) },
            )
        }
        val feedback = state.feedback
        when {
            feedback != null && feedback.correct -> {
                CorrectFeedbackBanner()
                // 答对后短暂停留，确保视觉和声音反馈都能被感知。
                LaunchedEffect(feedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    kotlinx.coroutines.delay(650)
                    onAdvance()
                }
            }
            feedback != null && !feedback.correct -> {
                FeedbackBanner("答错了，正确答案：${feedback.correctText}")
                LaunchedEffect(feedback) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
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
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (bg, fg, border) = when (highlight) {
        ReviewOptionHighlight.Correct -> Triple(
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer,
            BorderStroke(1.dp, colorScheme.tertiary),
        )
        ReviewOptionHighlight.Wrong -> Triple(
            colorScheme.errorContainer,
            colorScheme.onErrorContainer,
            BorderStroke(1.dp, colorScheme.error),
        )
        null -> Triple(
            colorScheme.surfaceContainerLow,
            colorScheme.onSurface,
            BorderStroke(1.dp, colorScheme.outlineVariant),
        )
    }
    val optionLabel = ('A' + index).toString()
    val state = when (highlight) {
        ReviewOptionHighlight.Correct -> if (isSelected) "已选，回答正确" else "正确答案"
        ReviewOptionHighlight.Wrong -> "已选，回答错误"
        null -> if (enabled) "未选" else "已锁定"
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "option-press-scale",
    )
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(14.dp),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                role = Role.RadioButton
                contentDescription = "选项 $optionLabel：$text"
                stateDescription = state
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(optionLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedbackBanner(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onErrorContainer,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 答对反馈：成功横幅 + 对勾一次性放大回弹和一圈扩散光环。用 [Animatable] 跑单次动画
 * 而不是 infiniteRepeatable——横幅只存在约 650ms（随后自动进入下一题），持续循环的脉冲
 * 在这么短的时间里只会呈现为抖动。
 */
@Composable
private fun CorrectFeedbackBanner() {
    val colorScheme = MaterialTheme.colorScheme
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = LinearEasing),
        )
    }
    // 0 → 1 的进度映射成「先冲到 1.25 倍再回落到 1 倍」的回弹，以及一圈向外扩散并淡出的光环。
    val progress = pop.value
    val iconScale = if (progress < 0.45f) {
        1f + (progress / 0.45f) * 0.25f
    } else {
        1.25f - ((progress - 0.45f) / 0.55f) * 0.25f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics {
                contentDescription = "回答正确，正在进入下一题"
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 扩散光环在短暂反馈中强调成功，不干扰下一题阅读。
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            val ring = 0.7f + progress * 0.45f
                            scaleX = ring
                            scaleY = ring
                            alpha = (1f - progress) * 0.45f
                        }
                        .background(colorScheme.tertiary, RoundedCornerShape(percent = 50)),
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colorScheme.tertiary,
                    modifier = Modifier
                        .size(26.dp)
                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                )
            }
            Text(
                "回答正确，正在进入下一题",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---- 学习 / 自由刷词 ----------------------------------------------------------------

@Composable
private fun LearnFlow(
    state: StudyScreenState.Ready,
    onMarkLearned: () -> Unit,
    onDefer: () -> Unit,
    onStartImmediateTest: () -> Unit,
    onContinueFreePlay: () -> Unit,
    onExitFreePlay: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    phraseStates: Map<String, PhraseUiState>,
    onTranslate: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onScopeChange: (StudyScope) -> Unit = {},
    playingKey: String? = null,
    speakingKey: String? = null,
    onReload: () -> Unit,
) {
    val isFree = state.phase == StudyPhase.FREE_PLAY
    val card = state.card
    val haptic = LocalHapticFeedback.current
    val cardScrollState = rememberScrollState()
    LaunchedEffect(card?.id) { cardScrollState.scrollTo(0) }
    ResponsiveContentBox(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        ScopeFilterRow(
            scope = state.scope,
            availableCurriculumTags = state.availableCurriculumTags,
            onScopeChange = onScopeChange,
        )
        GoalStepper(goal = state.dailyGoal, onSetGoal = onSetGoal)
        StudyProgressBar(state)
        if (!isFree && card != null) {
            Text(
                text = "本轮还剩 ${state.queueRemaining} 个新词",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().semantics {
                    stateDescription = "学习队列剩余 ${state.queueRemaining} 个单词"
                },
                textAlign = TextAlign.Center,
            )
        }
        if (isFree) {
            Text(
                text = "自由刷词中 · 不计入今日进度与明日复习",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        // 卡片区域随屏幕高度滚动（小屏 / 大字体不裁剪），顶部进度与底部操作始终可见。
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (card != null) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(cardScrollState),
                ) {
                    LearnCard(
                        word = card,
                        phraseStates = phraseStates,
                        onTranslate = onTranslate,
                        onSpeak = onSpeak,
                        onPlayPronunciation = onPlayPronunciation,
                        playingKey = playingKey,
                        speakingKey = speakingKey,
                    )
                }
            } else {
                StudyEmpty(
                    onReload = onReload,
                    onScopeChange = onScopeChange,
                    availableCurriculumTags = state.availableCurriculumTags,
                    currentScope = state.scope,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDefer,
                enabled = card != null,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text("稍后再看")
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMarkLearned()
                },
                enabled = card != null,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text(if (isFree) "刷完" else "我已背会")
            }
        }
        // 无需等明日：今天已背会的词可立即进入考试测试，回答正确即提前推进复习计划。
        if (!isFree && state.todayDone >= 1) {
            OutlinedButton(
                onClick = onStartImmediateTest,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("立即测试今日所学 · 无需等明日")
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
}

@Composable
private fun LearnCard(
    word: WordEntity,
    phraseStates: Map<String, PhraseUiState>,
    onTranslate: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    playingKey: String? = null,
    speakingKey: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            WordCardContent(
                word = word,
                phraseStates = phraseStates,
                onPlayPronunciation = onPlayPronunciation,
                onTranslate = onTranslate,
                onSpeak = onSpeak,
                playingKey = playingKey,
                speakingKey = speakingKey,
                showPartOfSpeech = true,
                modifier = Modifier.fillMaxWidth(),
                bottomContent = {
                    if (wordHasAnnotations(word)) {
                        HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
                        WordAnnotationSection(
                            word = word,
                            phraseStates = phraseStates,
                            onTranslate = onTranslate,
                            onSpeak = onSpeak,
                            speakingKey = speakingKey,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun StudyProgressBar(state: StudyScreenState.Ready) {
    val fraction = if (state.dailyGoal > 0) state.todayDone.toFloat() / state.dailyGoal else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "progress-fraction",
    )
    val isComplete = state.dailyGoal > 0 && state.todayDone >= state.dailyGoal
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("今日进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${state.todayDone} / ${state.dailyGoal}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isComplete) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .semantics {
                    contentDescription = "今日学习进度"
                    stateDescription = if (isComplete) {
                        "今日目标已达成，已背会 ${state.todayDone} 个单词"
                    } else {
                        "已背会 ${state.todayDone} 个单词，距目标还差 ${(state.dailyGoal - state.todayDone).coerceAtLeast(0)} 个"
                    }
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = animatedFraction,
                        range = 0f..1f,
                    )
                },
        )
        if (isComplete) {
            Text(
                "今日目标已达成！",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GoalStepper(goal: Int, onSetGoal: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "每日背词量设置"
                stateDescription = "当前每日背词量 $goal 个"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = { onSetGoal(goal - DAILY_GOAL_STEP) },
            enabled = goal > DAILY_GOAL_MIN,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少每日背词量")
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text("每日背词量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$goal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        IconButton(
            onClick = { onSetGoal(goal + DAILY_GOAL_STEP) },
            enabled = goal < DAILY_GOAL_MAX,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加每日背词量")
        }
    }
}

// ---- 完成 / 总结 --------------------------------------------------------------------

@Composable
private fun DoneFlow(
    state: StudyScreenState.Ready,
    onStartImmediateTest: () -> Unit,
    onContinueFreePlay: () -> Unit,
    onSetGoal: (Int) -> Unit,
    onScopeChange: (StudyScope) -> Unit = {},
) {
    ResponsiveContentBox(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "summary") {
            ScopeFilterRow(
                scope = state.scope,
                availableCurriculumTags = state.availableCurriculumTags,
                onScopeChange = onScopeChange,
            )
            GoalStepper(goal = state.dailyGoal, onSetGoal = onSetGoal)
            StudyProgressBar(state)
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "今日学习已达标",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "已背会 ${state.todayDone} 个单词，明天将安排复习。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    // 无需等明日：达标后仍可立即测试今天背会的词，正确即提前推进复习计划。
                    OutlinedButton(
                        onClick = onStartImmediateTest,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
                    ) {
                        Text("立即测试今日所学（无需等明日）")
                    }
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
                Text(
                    "今日已学清单 · ${state.learnedToday.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(state.learnedToday, key = { it.id }) { word ->
                LearnedWordRow(word)
            }
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