package com.chloemlla.cdict.ui.about

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.net.DonationChannel
import com.chloemlla.cdict.core.net.DonationClient
import com.chloemlla.cdict.core.net.DonationInfo
import com.chloemlla.cdict.core.net.DonationOutcome
import com.chloemlla.cdict.ui.ResponsiveContentBox
import kotlinx.coroutines.launch

private sealed interface DonationUiState {
    data object Loading : DonationUiState
    data class Ready(val info: DonationInfo) : DonationUiState
    data class Failed(val message: String) : DonationUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationScreen(onBack: () -> Unit) {
    val client = remember { DonationClient() }
    var state by remember { mutableStateOf<DonationUiState>(DonationUiState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        state = DonationUiState.Loading
        state = when (val outcome = client.fetchChannels()) {
            is DonationOutcome.Success -> DonationUiState.Ready(outcome.info)
            is DonationOutcome.Failure -> DonationUiState.Failed(outcome.message)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("赞赏支持", modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        ResponsiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                item {
                    SelectionContainer {
                        Text(
                            (state as? DonationUiState.Ready)?.info?.notice ?: DONATION_NOTICE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                when (val current = state) {
                    is DonationUiState.Loading -> item { DonationPlaceholder("正在获取赞赏码…", true) }
                    is DonationUiState.Failed -> item {
                        DonationPlaceholder("赞赏码获取失败：${current.message}", false)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { reloadKey++ }) { Text("重试") }
                    }
                    is DonationUiState.Ready -> items(current.info.channels.size) { index ->
                        DonationChannelCard(client, current.info.channels[index])
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DonationChannelCard(client: DonationClient, channel: DonationChannel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(channel.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(channel.id) { mutableStateOf(false) }
    var saving by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        val bytes = client.fetchImage(channel.id)
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap == null) failed = true else source = bitmap
    }
    val save: () -> Unit = {
        val bitmap = source
        if (bitmap != null && !saving) {
            saving = true
            scope.launch {
                val saved = GallerySaver.savePng(context, bitmap, "CDict-${channel.id}")
                saving = false
                Toast.makeText(
                    context,
                    if (saved) "已保存到相册 Pictures/CDict" else "保存失败，请检查存储空间后重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            save()
        } else {
            Toast.makeText(context, "没有存储权限，无法保存到相册", Toast.LENGTH_LONG).show()
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(channel.name, style = MaterialTheme.typography.titleMedium)
        }
        channel.hint?.let { hint ->
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = source
            val image = remember(bitmap) { bitmap?.asImageBitmap() }
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = "${channel.name}收款码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
                failed -> Text(
                    "图片加载失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> CircularProgressIndicator()
            }
        }
        if (source != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (GallerySaver.hasStoragePermission(context)) {
                        save()
                    } else {
                        permissionLauncher.launch(GallerySaver.STORAGE_PERMISSION)
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "正在保存…" else "保存${channel.name}收款码到相册")
            }
        }
    }
}

@Composable
private fun DonationPlaceholder(text: String, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val DONATION_NOTICE =
    "赞赏完全自愿：本应用永久免费，所有功能都不需要付费解锁，赞赏与否不影响任何使用体验。" +
        "收款码由服务端实时下发，安装包内不内置任何收款信息。"
