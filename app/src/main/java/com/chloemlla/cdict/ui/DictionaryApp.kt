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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
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
    onOpenDerivedWord: (WordEntity) -> Unit = {},
    onBackFromDetail: () -> Unit = {
        // Default: close the detail and return to the browse list.
        (state as? DictionaryScreenState.Ready)?.selected?.let { onSelect(it.copy(id = -1)) }
    },
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    onLoadMore: () -> Unit,
    onSortModeChanged: (SortMode) -> Unit,
    masteredIds: Set<Long> = emptySet(),
    onToggleMastered: (WordEntity) -> Unit = {},
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
            // System back (button or edge-swipe gesture) leaves the word detail. Whether it
            // returns to the browse list or to another tab depends on how the detail was opened;
            // the shell owns that decision via onBackFromDetail.
            BackHandler(enabled = selected != null) {
                onBackFromDetail()
            }
            // Save the browse list's state (scroll position, search text) while a detail is
            // showing, so going back lands where the user left off instead of at the top.
            val detailStateHolder = rememberSaveableStateHolder()
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
                detailStateHolder.SaveableStateProvider(
                    key = if (sel != null) "detail-${sel.id}" else "list",
                ) {
                    if (sel != null) {
                        WordDetail(
                            word = sel,
                            detail = state.detail,
                            onBack = onBackFromDetail,
                            onOpenWord = onOpenDerivedWord,
                            onPlayPronunciation = onPlayPronunciation,
                            phraseStates = phraseStates,
                            onPhraseTranslate = phraseViewModel::translate,
                            onPhraseSpeak = phraseViewModel::speak,
                            masteredIds = masteredIds,
                            onToggleMastered = onToggleMastered,
                        )
                    } else {
                        WordList(state, onQueryChanged, onSelect, onLoadMore, onSortModeChanged)
                    }
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
    // Key on the live text-field value, not the debounced committed query, so a search the
    // user just typed (but the view model hasn't yet applied) still clears on back.
    BackHandler(enabled = query.isNotBlank()) {
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
        ResponsiveContentBox(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
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
                    suggestion = state.suggestion,
                    onSuggestionClick = { suggestion ->
                        query = suggestion.word
                        onQueryChanged(suggestion.word)
                    },
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
}

@Composable
private fun ColumnScope.EmptySearchState(
    query: String,
    suggestion: WordEntity?,
    onSuggestionClick: (WordEntity) -> Unit,
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
                // "Did you mean?" (PRD §3.1): the closest headword within edit distance <= 2,
                // shown because nothing matched the typed query.
                if (suggestion != null) {
                    OutlinedCard(
                        onClick = { onSuggestionClick(suggestion) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "是否查找：${suggestion.word}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
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
    onOpenWord: (WordEntity) -> Unit,
    onPlayPronunciation: (WordEntity, Accent) -> Unit,
    phraseStates: Map<String, PhraseUiState>,
    onPhraseTranslate: (String) -> Unit,
    onPhraseSpeak: (String) -> Unit,
    masteredIds: Set<Long>,
    onToggleMastered: (WordEntity) -> Unit,
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
    val derivedTermWords = detail?.derivedTermWords.orEmpty()
    val heatmap = detail?.heatmap.orEmpty().filter { it.period.isNotBlank() }
    val sentences = detail?.sentences.orEmpty().filter { it.english.isNotBlank() }
    val supplements = supplementedFields(word)

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
                            contentDescription = "返回"
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
        ResponsiveContentBox(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
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
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(
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
                                if ("phoneticUk" in supplements || "phoneticUs" in supplements) {
                                    AiSupplementPill()
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
                        val mastered = word.id in masteredIds
                        OutlinedButton(
                            onClick = { onToggleMastered(word) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .heightIn(min = 48.dp),
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) {
                            Icon(
                                imageVector = if (mastered) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = null,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(if (mastered) "已掌握 · 移出背词计划" else "加入背词计划")
                        }
                    }
                }
            }

            if (wordHasAnnotations(word)) {
                item(key = "annotations") {
                    DetailSectionCard(title = "AI 语感标注") {
                        WordAnnotationSection(
                            word = word,
                            phraseStates = phraseStates,
                            onTranslate = onPhraseTranslate,
                            onSpeak = onPhraseSpeak,
                        )
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
                    DetailSectionCard(
                        title = "记忆提示",
                        trailing = { if ("mnemonic" in supplements) AiSupplementPill() },
                    ) {
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
                        DetailSectionCard(
                            title = "派生词",
                            trailing = { if ("derived" in supplements) AiSupplementPill() },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                derivedTerms.forEach { term ->
                                    val targetWord = derivedTermWords[term.lowercase()]
                                    SpeakableEnglishText(
                                        en = term,
                                        pinnedZh = null,
                                        ui = phraseStates[term],
                                        onTranslate = onPhraseTranslate,
                                        onSpeak = onPhraseSpeak,
                                        modifier = Modifier.fillMaxWidth(),
                                        trailing = if (targetWord == null) null else {
                                            { TextButton(onClick = { onOpenWord(targetWord) }) { Text("前往") } }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (heatmap.isNotEmpty()) {
                    item(key = "heatmap") {
                        DetailSectionCard(title = "历年出现频率") {
                            val maxScore = heatmap.maxOf { it.score }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                heatmap.forEach { entry ->
                                    val fraction = if (maxScore > 0) (entry.score / maxScore).toFloat() else 0f
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = entry.period,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(min = 56.dp),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(14.dp)
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction = fraction.coerceIn(0f, 1f))
                                                    .background(MaterialTheme.colorScheme.primary),
                                            )
                                        }
                                        Text(
                                            text = entry.score.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.widthIn(min = 40.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "sentences") {
                    DetailSectionCard(
                        title = "真题句子",
                        trailing = { if ("sentences" in supplements) AiSupplementPill() },
                    ) {
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
}


@Composable
private fun DetailSectionCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                trailing?.invoke()
            }
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
