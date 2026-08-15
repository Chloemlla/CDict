package com.chloemlla.cdict.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.core.translate.TranslationDirection
import com.chloemlla.cdict.core.translate.TranslationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(viewModel: TranslationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("翻译") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("vivo 翻译引擎 · 在线翻译", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranslationDirection.entries.forEach { d ->
                    FilterChip(
                        selected = d == direction,
                        onClick = { viewModel.onDirectionChange(d) },
                        label = { Text(d.label) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入要翻译的文本") },
                minLines = 3,
                maxLines = 6,
            )
            Button(
                onClick = viewModel::translate,
                enabled = query.isNotBlank() && state !is TranslationUiState.Translating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("翻译")
            }
            when (val s = state) {
                TranslationUiState.Idle -> Text(
                    "输入文本后点击「翻译」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TranslationUiState.Translating -> Row {
                    CircularProgressIndicator(Modifier.padding(top = 4.dp))
                    Text("翻译中…", Modifier.padding(start = 12.dp, top = 8.dp))
                }
                is TranslationUiState.Success -> TranslationResultBlock(s.result)
                is TranslationUiState.Failure -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TranslationResultBlock(result: TranslationResult) {
    Column(Modifier.fillMaxWidth()) {
        Text("译文", style = MaterialTheme.typography.labelMedium)
        result.translations.forEachIndexed { index, translation ->
            Text(
                translation,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = if (index == 0) 8.dp else 4.dp),
            )
        }
        val meta = mutableListOf<String>()
        if (result.from.isNotEmpty()) meta.add("源: ${result.from}")
        if (result.to.isNotEmpty()) meta.add("目标: ${result.to}")
        result.phonetic?.let { meta.add("音标: $it") }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
