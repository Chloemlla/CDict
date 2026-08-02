package com.chloemlla.cdict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.ui.DictionaryApp
import com.chloemlla.cdict.ui.DictionaryViewModel
import com.chloemlla.cdict.ui.DictionaryViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: DictionaryViewModel by viewModels {
        DictionaryViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    DictionaryApp(state = state, onQueryChanged = viewModel::search, onSelect = viewModel::select)
                }
            }
        }
    }
}
