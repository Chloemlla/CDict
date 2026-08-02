package com.chloemlla.cdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.data.WordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryApp(
    state: DictionaryScreenState,
    onQueryChanged: (String) -> Unit,
    onSelect: (WordEntity) -> Unit,
) {
    when (state) {
        DictionaryScreenState.Loading -> LoadingScreen()
        is DictionaryScreenState.Error -> ErrorScreen(state.message)
        is DictionaryScreenState.Ready -> {
            if (state.selected != null) {
                WordDetail(word = state.selected, onBack = { onSelect(state.selected.copy(id = -1)) })
            } else {
                WordList(state, onQueryChanged, onSelect)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.padding(24.dp))
        Text("正在加载离线词典…", Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("无法打开词典", style = MaterialTheme.typography.headlineSmall)
        Text(message, Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordList(state: DictionaryScreenState.Ready, onQueryChanged: (String) -> Unit, onSelect: (WordEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    Scaffold(topBar = { TopAppBar(title = { Text("CDict IELTS Dictionary") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onQueryChanged(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索英文或中文") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.words, key = { it.id }) { word ->
                    ListItem(
                        headlineContent = { Text(word.word) },
                        supportingContent = { Text(listOfNotNull(word.phoneticUk, word.translation).joinToString("  ")) },
                        modifier = Modifier.clickable { onSelect(word) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordDetail(word: WordEntity, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(word.word) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text(word.word, style = MaterialTheme.typography.headlineMedium) }
            item { Text(listOfNotNull(word.phoneticUk, word.phoneticUs).joinToString("   "), Modifier.padding(top = 8.dp)) }
            item { Text(word.translation.orEmpty(), Modifier.padding(top = 16.dp)) }
            item { Text(word.definition.orEmpty(), Modifier.padding(top = 8.dp)) }
            item { Text("频率组 ${word.frequencyGroup} · IELTS 频率 ${word.frequency}", Modifier.padding(top = 16.dp)) }
            item { Text(word.mnemonic.orEmpty(), Modifier.padding(top = 16.dp)) }
            item { Text("真题句子将在此处按每页 10 条显示。", Modifier.padding(top = 24.dp)) }
        }
    }
}
