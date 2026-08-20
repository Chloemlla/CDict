package com.chloemlla.cdict.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.audio.PronunciationPlayer
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationResult

private const val MAX_QUERY_LENGTH = 2_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(viewModel: TranslationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val supportedLanguages by viewModel.supportedLanguages.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val canTranslate = query.isNotBlank() && state !is TranslationUiState.Translating

    val buttonInteractionSource = remember { MutableInteractionSource() }
    val buttonPressed by buttonInteractionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "translateButtonScale",
    )

    val context = LocalContext.current
    val pronunciationPlayer = remember { PronunciationPlayer(context) }
    DisposableEffect(Unit) { onDispose { pronunciationPlayer.release() } }

    // 当前正在朗读的译文；播放自然结束自动清空，同一条再点一次即停止。
    var speakingText by remember { mutableStateOf<String?>(null) }
    val onSpeakTranslation: (String) -> Unit = { text ->
        if (speakingText == text) {
            pronunciationPlayer.stop()
            speakingText = null
        } else {
            pronunciationPlayer.onCompletion = { speakingText = null }
            pronunciationPlayer.play(text, Accent.US)
            speakingText = text
        }
    }

    LaunchedEffect(Unit) { viewModel.loadSupportedLanguages() }

    // 结果卡片消失（改输入 / 重新翻译）时停掉正在播放的朗读，避免"看不到来源的声音"。
    LaunchedEffect(state) {
        if (state !is TranslationUiState.Success && speakingText != null) {
            pronunciationPlayer.stop()
            speakingText = null
        }
    }

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
                                imageVector = Icons.Filled.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Column {
                            Text("翻译", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "vivo 翻译引擎",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "即时翻译",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "把想表达的内容交给我们。",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "选择翻译方向，输入原文后即可获得译文。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                        Text(
                            "翻译方向",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    // 互换按钮只在存在反向方向时出现（auto→X 没有可互换的源语言）。
                    if (direction.canSwap) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onDirectionChange(direction.swapped())
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "互换翻译方向，改为${direction.swapped().label}"
                                stateDescription = "当前方向：${direction.label}"
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TranslationDirection.entries.forEach { item ->
                        FilterChip(
                            selected = item == direction,
                            onClick = { viewModel.onDirectionChange(item) },
                            label = { Text(item.label) },
                            leadingIcon = if (item == direction) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                contentDescription = "翻译方向：${item.label}"
                                stateDescription = if (item == direction) "已选择" else "未选择"
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it.take(MAX_QUERY_LENGTH)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("原文") },
                placeholder = { Text("输入要翻译的文本") },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { viewModel.onQueryChange("") },
                            modifier = Modifier.semantics {
                                contentDescription = "清除原文"
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null,
                            )
                        }
                    }
                } else {
                    null
                },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "回车即可翻译，可粘贴多行文本",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${query.length}/$MAX_QUERY_LENGTH",
                            maxLines = 1,
                            color = if (query.length >= MAX_QUERY_LENGTH) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (canTranslate) viewModel.translate()
                    },
                ),
            )

            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.translate()
                },
                enabled = canTranslate,
                interactionSource = buttonInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .semantics {
                        stateDescription = when {
                            state is TranslationUiState.Translating -> "翻译中"
                            query.isBlank() -> "请输入原文后翻译"
                            else -> "可以翻译"
                        }
                    },
            ) {
                if (state is TranslationUiState.Translating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在翻译")
                } else {
                    Icon(
                        imageVector = Icons.Filled.Translate,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("翻译")
                }
            }

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(250)) +
                        slideInVertically(tween(250)) { it / 6 }) togetherWith
                        (fadeOut(tween(180)) +
                            shrinkVertically(tween(180))) using
                        SizeTransform(clip = true)
                },
                label = "translation state",
            ) { currentState ->
                when (currentState) {
                    TranslationUiState.Idle -> EmptyTranslationState()
                    TranslationUiState.Translating -> ShimmerSkeleton()
                    is TranslationUiState.Success -> TranslationResultBlock(
                        result = currentState.result,
                        originalText = query,
                        speakingText = speakingText,
                        onSpeak = onSpeakTranslation,
                    )
                    is TranslationUiState.Failure -> FailureState(
                        message = currentState.message,
                        onRetry = viewModel::translate,
                        enabled = canTranslate,
                    )
                }
            }

            if (supportedLanguages.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp),
                    )
                    SelectionContainer {
                        Text(
                            "支持语种：${supportedLanguages.joinToString(" ")} 等",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyTranslationState() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 主插图卡片保持静态，避免空态被持续动效打扰。
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                Text(
                    "准备开始翻译",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "在上方输入文本并选择翻译方向，译文会显示在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        // 使用提示卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "使用技巧",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TranslateTip("多行输入", "可粘贴长文本、段落做整段翻译")
                    TranslateTip("自动检测语言", "选择「自动检测 → 目标语言」无需手动选源语言")
                    TranslateTip("朗读译文", "英文译文点击喇叭朗读，再点一次停止")
                    TranslateTip("清除重输", "点击输入框右侧 ✕ 快速清空")
                    TranslateTip("交换语言", "点击翻译方向右侧 ⇄ 快速互译")
                }
            }
        }
    }
}

@Composable
private fun TranslateTip(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShimmerSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .graphicsLayer { this.alpha = alpha },
                ) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                        Spacer(Modifier.fillMaxSize())
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonLine(widthFraction = 0.4f, height = 20.dp, alpha = alpha)
                    SkeletonLine(widthFraction = 0.25f, height = 14.dp, alpha = alpha)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            SkeletonLine(widthFraction = 1f, height = 18.dp, alpha = alpha)
            SkeletonLine(widthFraction = 0.85f, height = 18.dp, alpha = alpha)
            SkeletonLine(widthFraction = 0.6f, height = 18.dp, alpha = alpha)
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .graphicsLayer { this.alpha = alpha },
    ) {
        Surface(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) {
            Spacer(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun FailureState(
    message: String,
    onRetry: () -> Unit,
    enabled: Boolean,
) {
    val shake = remember { Animatable(0f) }
    var detailShown by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) {
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 400
                val amplitude = 10f
                0f at 0
                -amplitude at 50
                amplitude at 100
                -amplitude * 0.6f at 150
                amplitude * 0.6f at 200
                -amplitude * 0.3f at 250
                0f at 300
            },
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "翻译未完成",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        friendlyFailureHint(message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(
                        onClick = { detailShown = !detailShown },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(
                            if (detailShown) "收起错误详情" else "查看错误详情",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (detailShown) {
                        SelectionContainer {
                            Text(
                                message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onRetry,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("再次尝试")
            }
        }
    }
}

// 把接口/网络层的技术性错误串翻成用户能读懂的中文提示；原始串仍可在"查看错误详情"里展开。
private fun friendlyFailureHint(message: String): String = when {
    message.contains("网络请求失败") ||
        message.contains("网络异常") ||
        message.contains("timeout", ignoreCase = true) ||
        message.contains("Unable to resolve host", ignoreCase = true) ->
        "网络连接不稳定，请检查网络后重试。"
    message.contains("401") || message.contains("签名") -> "翻译服务鉴权失败，请稍后再试。"
    message.contains("非 JSON") -> "翻译服务返回了异常内容，请稍后再试。"
    message.contains("服务端错误") || message.contains("HTTP 5") -> "翻译服务暂时不可用，请稍后再试。"
    else -> "这次翻译没有完成，请稍后重试或换个说法。"
}

@Composable
private fun TranslationResultBlock(
    result: TranslationResult,
    originalText: String,
    speakingText: String?,
    onSpeak: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val copyText = result.translations.joinToString("\n")
    var copied by remember(result) { mutableStateOf(false) }
    // 长原文默认折叠 3 行；hasVisualOverflow 决定是否需要"展开"入口（展开后保持入口可见以便收起）。
    var originalExpanded by remember(originalText) { mutableStateOf(false) }
    var originalTruncated by remember(originalText) { mutableStateOf(false) }
    val metadata = buildList {
        if (result.from.isNotEmpty()) add("来源语言" to languageLabel(result.from))
        if (result.to.isNotEmpty()) add("目标语言" to languageLabel(result.to))
        result.phonetic?.takeIf { it.isNotEmpty() }?.let { add("音标" to it) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "翻译结果",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        if (copied) "已复制到剪贴板" else "已完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
                if (copyText.isNotBlank()) {
                    IconButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText(null, copyText))
                            copied = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (copied) "已复制译文" else "复制译文"
                            stateDescription = if (copied) "已复制" else "未复制"
                        },
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (originalText.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "原文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    SelectionContainer {
                        Text(
                            originalText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = if (originalExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layout ->
                                if (!originalExpanded) originalTruncated = layout.hasVisualOverflow
                            },
                        )
                    }
                    if (originalTruncated) {
                        TextButton(
                            onClick = { originalExpanded = !originalExpanded },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(
                                if (originalExpanded) "收起原文" else "展开全部原文",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))

            if (result.translations.isEmpty()) {
                Text(
                    "暂时没有可显示的译文，请尝试调整原文或翻译方向。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                val speakable = result.to.equals("en", ignoreCase = true)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "译文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.translations.forEach { translation ->
                                val speaking = speakingText == translation
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        translation,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (speakable) {
                                        IconButton(
                                            onClick = { onSpeak(translation) },
                                            modifier = Modifier.semantics {
                                                contentDescription = if (speaking) {
                                                    "停止朗读 $translation"
                                                } else {
                                                    "朗读 $translation"
                                                }
                                                stateDescription = if (speaking) {
                                                    "正在朗读，再次点击停止"
                                                } else {
                                                    "未朗读"
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = if (speaking) {
                                                    Icons.Filled.Stop
                                                } else {
                                                    Icons.AutoMirrored.Filled.VolumeUp
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (metadata.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        metadata.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MetaPill(text = label)
                                Text(
                                    value,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 语种元数据显示为中文名并附原始代码（如「英语（en）」），未知代码原样展示。
private fun languageLabel(code: String): String {
    val name = when (code.lowercase()) {
        "auto" -> "自动检测"
        "zh", "zh-chs", "zh-cn", "zh-hans" -> "中文"
        "en" -> "英语"
        "ja" -> "日语"
        "ko" -> "韩语"
        "fr" -> "法语"
        "de" -> "德语"
        "es" -> "西班牙语"
        "ru" -> "俄语"
        else -> null
    }
    return if (name == null) code else "$name（$code）"
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}