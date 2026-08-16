package com.chloemlla.cdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chloemlla.cdict.R
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DictionaryApp(
    state: DictionaryScreenState,
    onQueryChanged: (String) -> Unit,
    onSelect: (WordEntity) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    onLoadMore: () -> Unit,
    onSortModeChanged: (SortMode) -> Unit,
) {
    val context = LocalContext.current
    val phraseViewModel: PhraseSpeechViewModel = viewModel(
        factory = remember { PhraseSpeechViewModelFactory(context) },
    )
    val phraseStates by phraseViewModel.states.collectAsStateWithLifecycle()
    when (state) {
        DictionaryScreenState.Loading -> LoadingScreen()
        is DictionaryScreenState.Error -> ErrorScreen(state.message)
        is DictionaryScreenState.Ready -> {
            val selected = state.selected
            // System back (button or edge-swipe gesture) returns from the word detail to the list.
            BackHandler(enabled = selected != null) {
                selected?.let { onSelect(it.copy(id = -1)) }
            }
            AnimatedContent(
                targetState = selected,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState != null) {
                        // Opening a word slides in from the right; the list fades underneath.
                        (slideInHorizontally(animationSpec = tween(durationMillis = 260)) { it } +
                            fadeIn(animationSpec = tween(durationMillis = 150)))
                            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 120)))
                    } else {
                        // Returning: the detail slides back out to the right while the list fades back in.
                        fadeIn(animationSpec = tween(durationMillis = 150))
                            .togetherWith(
                                slideOutHorizontally(animationSpec = tween(durationMillis = 260)) { it } +
                                    fadeOut(animationSpec = tween(durationMillis = 150))
                            )
                    }
                },
                label = "word detail navigation",
            ) { sel ->
                if (sel != null) {
                    WordDetail(
                        word = sel,
                        detail = state.detail,
                        onBack = { onSelect(sel.copy(id = -1)) },
                        onPlayPronunciation = onPlayPronunciation,
                        phraseStates = phraseStates,
                        onPhraseTranslate = phraseViewModel::translate,
                        onPhraseSpeak = phraseViewModel::speak,
                    )
                } else {
                    WordList(state, onQueryChanged, onSelect, onLoadMore, onSortModeChanged)
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = "正在加载离线词典"
                },
            )
            Text(
                text = "正在加载离线词典…",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "词典加载失败",
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "无法打开词典",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message.takeIf { it.isNotBlank() } ?: "无法读取本地词典数据。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WordList(
    state: DictionaryScreenState.Ready,
    onQueryChanged: (String) -> Unit,
    onSelect: (WordEntity) -> Unit,
    onLoadMore: () -> Unit,
    onSortModeChanged: (SortMode) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // System back (button or edge-swipe gesture) with a non-blank query clears the search
    // before exiting, mirroring standard search-box behaviour. Dismiss once blank.
    BackHandler(enabled = state.query.isNotBlank()) {
        keyboardController?.hide()
        focusManager.clearFocus()
        query = ""
        onQueryChanged("")
    }

    // Jump back to the top whenever a fresh search or sort change replaces the browse list.
    var lastListKey by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.query, state.sortMode) {
        val key = "${state.sortMode.name}:${state.query}"
        if (key != lastListKey) {
            lastListKey = key
            listState.scrollToItem(0)
        }
    }

    // Load more when the user scrolls near the end of the browse list.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.words.size) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore) {
            onLoadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = "CDict 图标",
                        )
                        Column {
                            Text(
                                text = "CDict",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "IELTS Dictionary",
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChanged(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics {
                        contentDescription = "搜索英文、中文或定义"
                    },
                label = { Text("搜索词典") },
                placeholder = { Text("输入英文单词或中文翻译") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                query = ""
                                onQueryChanged("")
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "清除搜索内容"
                            },
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )

            if (query.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.sortMode == mode,
                            onClick = { onSortModeChanged(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            if (state.words.isEmpty()) {
                EmptySearchState(
                    query = query,
                    onClear = {
                        query = ""
                        onQueryChanged("")
                    },
                )
            } else {
                Text(
                    text = if (query.isBlank()) "全部词条" else "匹配词条",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.words, key = { it.id }) { word ->
                        WordResultCard(
                            word = word,
                            onSelect = onSelect,
                        )
                    }
                    if (state.query.isBlank() && state.words.isNotEmpty()) {
                        item(key = "browse-footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    state.isLoadingMore -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            text = "加载更多…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    state.hasMore -> Text(
                                        text = "继续下滑加载更多",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    else -> Text(
                                        text = "已展示全部 ${state.words.size} 个词条",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptySearchState(
    query: String,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (query.isBlank()) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.SearchOff,
                contentDescription = if (query.isBlank()) "词典为空" else "没有找到匹配词条",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (query.isBlank()) "词典中暂无词条" else "没有找到匹配词条",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (query.isNotBlank()) {
                Text(
                    text = "试试英文单词、中文翻译或定义",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("清除搜索")
                }
            }
        }
    }
}

@Composable
private fun WordResultCard(
    word: WordEntity,
    onSelect: (WordEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val phonetics = listOfNotNull(
        word.phoneticUk?.takeIf { it.isNotBlank() }?.let { "英  $it" },
        word.phoneticUs?.takeIf { it.isNotBlank() }?.let { "美  $it" },
    )
    val translation = word.translation?.takeIf { it.isNotBlank() }
    val definition = word.definition?.takeIf { it.isNotBlank() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "查看单词 ${word.word}",
                role = Role.Button,
                onClick = { onSelect(word) },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MetadataPill(text = "组 ${word.frequencyGroup}")
            }
            if (phonetics.isNotEmpty()) {
                Text(
                    text = phonetics.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            translation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            definition?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataPill(text = "IELTS 频率 ${word.frequency}")
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WordDetail(
    word: WordEntity,
    detail: WordDetailData?,
    onBack: () -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    phraseStates: Map<String, PhraseUiState>,
    onPhraseTranslate: (String) -> Unit,
    onPhraseSpeak: (String) -> Unit,
) {
    val phoneticUk = word.phoneticUk?.takeIf { it.isNotBlank() }
    val phoneticUs = word.phoneticUs?.takeIf { it.isNotBlank() }
    val translation = word.translation?.takeIf { it.isNotBlank() }
    val definition = word.definition?.takeIf { it.isNotBlank() }
    val mnemonic = word.mnemonic?.takeIf { it.isNotBlank() }
    val roots = detail?.roots.orEmpty().filter { root ->
        root.root.isNotBlank() || !root.meaning.isNullOrBlank()
    }
    val derivedTerms = detail?.derivedTerms.orEmpty().filter { it.isNotBlank() }
    val heatmap = detail?.heatmap.orEmpty().filter { it.period.isNotBlank() }
    val sentences = detail?.sentences.orEmpty().filter { it.english.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = word.word,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "返回词典列表"
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "word-header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "词条",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (phoneticUk != null || phoneticUs != null) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                phoneticUk?.let {
                                    Text(
                                        text = "英式  $it",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                phoneticUs?.let {
                                    Text(
                                        text = "美式  $it",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        translation?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                        }
                        definition?.let {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "释义",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SpeakableEnglishText(
                                    en = it,
                                    pinnedZh = null,
                                    ui = phraseStates[it],
                                    onTranslate = onPhraseTranslate,
                                    onSpeak = onPhraseSpeak,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = "朗读",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onPlayPronunciation(word, Accent.UK) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                contentPadding = ButtonDefaults.ContentPadding,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("英音")
                            }
                            FilledTonalButton(
                                onClick = { onPlayPronunciation(word, Accent.US) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                contentPadding = ButtonDefaults.ContentPadding,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("美音")
                            }
                        }
                    }
                }
            }

            item(key = "frequency") {
                DetailSectionCard(title = "词频信息") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "频率组",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = word.frequencyGroup.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "IELTS 频率",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = word.frequency.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            if (mnemonic != null) {
                item(key = "mnemonic") {
                    DetailSectionCard(title = "记忆提示") {
                        SpeakableEnglishText(
                            en = mnemonic,
                            pinnedZh = null,
                            ui = phraseStates[mnemonic],
                            onTranslate = onPhraseTranslate,
                            onSpeak = onPhraseSpeak,
                        )
                    }
                }
            }

            if (detail == null) {
                item(key = "detail-loading") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = "正在加载更多词条信息…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                if (roots.isNotEmpty()) {
                    item(key = "roots") {
                        DetailSectionCard(title = "词根") {
                            roots.forEachIndexed { index, root ->
                                if (index > 0) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                                if (root.root.isNotBlank()) {
                                    SpeakableEnglishText(
                                        en = root.root,
                                        pinnedZh = root.meaning?.takeIf { it.isNotBlank() },
                                        ui = phraseStates[root.root],
                                        onTranslate = onPhraseTranslate,
                                        onSpeak = onPhraseSpeak,
                                    )
                                } else {
                                    root.meaning?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (derivedTerms.isNotEmpty()) {
                    item(key = "derived-terms") {
                        DetailSectionCard(title = "派生词") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                derivedTerms.forEach { term ->
                                    SpeakableEnglishText(
                                        en = term,
                                        pinnedZh = null,
                                        ui = phraseStates[term],
                                        onTranslate = onPhraseTranslate,
                                        onSpeak = onPhraseSpeak,
                                    )
                                }
                            }
                        }
                    }
                }

                if (heatmap.isNotEmpty()) {
                    item(key = "heatmap") {
                        DetailSectionCard(title = "历年出现频率") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                heatmap.forEach { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = entry.period,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Text(
                                            text = entry.score.toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "sentences") {
                    DetailSectionCard(title = "真题句子") {
                        if (sentences.isEmpty()) {
                            Text(
                                text = "暂无真题句子",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            sentences.forEachIndexed { index, sentence ->
                                if (index > 0) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                                SpeakableEnglishText(
                                    en = sentence.english,
                                    pinnedZh = sentence.chinese?.takeIf { it.isNotBlank() },
                                    ui = phraseStates[sentence.english],
                                    onTranslate = onPhraseTranslate,
                                    onSpeak = onPhraseSpeak,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakableEnglishText(
    en: String,
    pinnedZh: String?,
    ui: PhraseUiState?,
    onTranslate: (String) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(en) {
        if (pinnedZh == null && ui == null) onTranslate(en)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = en,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onSpeak(en) },
                modifier = Modifier.semantics { contentDescription = "朗读 $en" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            !pinnedZh.isNullOrBlank() -> Text(
                text = pinnedZh,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui is PhraseUiState.Loading -> Text(
                text = "译中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            ui is PhraseUiState.Done -> Text(
                text = ui.zh,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui is PhraseUiState.Error -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "翻译失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = { onTranslate(en) }) { Text("重试") }
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun MetadataPill(text: String) {
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
