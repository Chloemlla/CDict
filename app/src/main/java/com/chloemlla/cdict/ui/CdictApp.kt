package com.chloemlla.cdict.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chloemlla.cdict.core.audio.Accent
import com.chloemlla.cdict.core.data.WordEntity

@Composable
fun CdictApp(
    dictionaryState: DictionaryScreenState,
    onDictionaryQueryChanged: (String) -> Unit,
    onDictionarySelect: (WordEntity) -> Unit,
    onDictionaryPlayPronunciation: (WordEntity, Accent) -> Unit,
    translationViewModel: TranslationViewModel,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                    label = { Text("词典") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                    label = { Text("翻译") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DictionaryApp(
                    state = dictionaryState,
                    onQueryChanged = onDictionaryQueryChanged,
                    onSelect = onDictionarySelect,
                    onPlayPronunciation = onDictionaryPlayPronunciation,
                )
                else -> TranslateScreen(translationViewModel)
            }
        }
    }
}
