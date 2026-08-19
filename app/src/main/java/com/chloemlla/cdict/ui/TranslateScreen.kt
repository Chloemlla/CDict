package com.chloemlla.cdict.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.audio.PronunciationPlayer
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(viewModel: TranslationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val supportedLanguages by viewModel.supportedLanguages.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
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

    LaunchedEffect(Unit) { viewModel.loadSupportedLanguages() }

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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "即时翻译",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
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
                    )
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
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
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
                    ) {
                        Text("支持多行输入")
                        Spacer(Modifier.weight(1f))
                        Text("${query.length} 字符")
                    }
                },
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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
                        onSpeak = { translation -> pronunciationPlayer.play(translation, Accent.US) },
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

@Composable
private fun EmptyTranslationState() {
    StateCard(
        icon = Icons.Filled.Info,
        iconDescription = "等待输入",
        title = "准备开始",
        message = "在上方输入文本并选择翻译方向，译文会显示在这里。",
    )
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
                    contentDescription = "翻译失败",
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
                    )
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
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

@Composable
private fun StateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDescription: String,
    title: String,
    message: String,
    showProgress: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(10.dp),
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconDescription,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TranslationResultBlock(
    result: TranslationResult,
    onSpeak: (String) -> Unit,
) {
    val metadata = buildList {
        if (result.from.isNotEmpty()) add("来源语言" to result.from)
        if (result.to.isNotEmpty()) add("目标语言" to result.to)
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
                        contentDescription = "翻译成功",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column {
                    Text(
                        "翻译结果",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "已完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
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
                    result.translations.forEach { translation ->
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
                                        contentDescription = "朗读 $translation"
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (metadata.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
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
