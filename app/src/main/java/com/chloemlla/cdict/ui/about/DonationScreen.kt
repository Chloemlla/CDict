package com.chloemlla.cdict.ui.about

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.app.ActivityCompat
import com.chloemlla.cdict.core.net.DonationChannel
import com.chloemlla.cdict.core.net.DonationClaimOutcome
import com.chloemlla.cdict.core.net.DonationClient
import com.chloemlla.cdict.core.net.DonationInfo
import com.chloemlla.cdict.core.net.DonationOutcome
import com.chloemlla.cdict.ui.ResponsiveContentBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface DonationUiState {
    data object Loading : DonationUiState
    data class Ready(val info: DonationInfo) : DonationUiState
    data class Failed(val message: String) : DonationUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val client = remember { DonationClient() }
    val store = remember(context) { AboutStore(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DonationUiState>(DonationUiState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var confettiKey by remember { mutableStateOf(0) }
    var transactionId by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ClaimFeedback?>(null) }
    LaunchedEffect(reloadKey) {
        state = DonationUiState.Loading
        state = when (val outcome = client.fetchChannels()) {
            is DonationOutcome.Success -> DonationUiState.Ready(outcome.info)
            is DonationOutcome.Failure -> DonationUiState.Failed(outcome.message)
        }
    }
    // 提交状态挂在页面级：署名表单是 LazyColumn 的一个 item，滚出视口就会连同它的协程一起被回收。
    val submit: () -> Unit = submit@{
        if (submitting) return@submit
        val submittedId = transactionId.trim()
        if (!isWellFormedTransactionId(submittedId)) {
            feedback = ClaimFeedback("交易号只能是 6-64 位的字母、数字、连字符或下划线", false)
            return@submit
        }
        val now = System.currentTimeMillis()
        val quota = store.donationClaimQuota
        val blocked = quota.rejectionAt(now)
        if (blocked != null) {
            feedback = ClaimFeedback(blocked, false)
            return@submit
        }
        store.donationClaimQuota = quota.accepted(now)
        submitting = true
        val submittedName = displayName
        scope.launch {
            val outcome = client.submitClaim(submittedId, submittedName)
            submitting = false
            when (outcome) {
                is DonationClaimOutcome.Accepted -> {
                    feedback = ClaimFeedback(outcome.message, true)
                    if (!outcome.duplicated) {
                        transactionId = ""
                        displayName = ""
                        confettiKey++
                    }
                }
                is DonationClaimOutcome.Rejected -> {
                    // 没有排进队列就不占用每小时额度，只保留最短间隔挡住连点。
                    store.donationClaimQuota = quota.copy(lastSubmitMillis = now)
                    feedback = ClaimFeedback(outcome.message, false)
                }
            }
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                        is DonationUiState.Ready -> {
                            items(current.info.channels.size) { index ->
                                DonationChannelCard(client, current.info.channels[index])
                                Spacer(Modifier.height(20.dp))
                            }
                            item(key = "supporters") {
                                DonationSupportSection(
                                    supporters = current.info.supporters,
                                    transactionId = transactionId,
                                    displayName = displayName,
                                    submitting = submitting,
                                    feedback = feedback,
                                    onTransactionIdChange = { transactionId = it },
                                    onDisplayNameChange = { displayName = it },
                                    onSubmit = submit,
                                )
                            }
                        }
                    }
                }
            }
            EmojiConfetti(burstKey = confettiKey, onFinished = { confettiKey = 0 })
        }
    }
}

@Composable
private fun DonationChannelCard(client: DonationClient, channel: DonationChannel) {
    val context = LocalContext.current
    val hostActivity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var source by remember(channel.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(channel.id) { mutableStateOf(false) }
    var saving by remember(channel.id) { mutableStateOf(false) }
    var imageReloadKey by remember(channel.id) { mutableStateOf(0) }
    LaunchedEffect(channel.id, imageReloadKey) {
        failed = false
        source = null
        // 图片来自后台填写的图床，尺寸不受安装包控制：先卡字节数，再按显示尺寸降采样，且都在 IO 线程。
        val bitmap = withContext(Dispatchers.IO) {
            client.fetchImage(channel.id)
                ?.takeIf { it.size <= MAX_QR_BYTES }
                ?.let { decodeSampled(it, MAX_QR_PIXELS) }
        }
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
                    if (saved) "已保存到相册 Pictures/CDict" else "保存失败，请稍后重试",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when {
            granted -> save()
            hostActivity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                hostActivity,
                GallerySaver.STORAGE_PERMISSION,
            ) -> {
                Toast.makeText(context, "存储权限已被拒绝，请在系统设置里允许后再保存", Toast.LENGTH_LONG).show()
                openAppSettings(hostActivity)
            }
            else -> Toast.makeText(context, "没有存储权限，无法保存到相册", Toast.LENGTH_LONG).show()
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
                failed -> TextButton(onClick = { imageReloadKey++ }) {
                    Text(
                        "图片加载失败，点这里重新加载",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
        "收款码地址与说明文案都由服务端实时下发，安装包内不内置任何收款信息。"

private const val MAX_QR_BYTES = 4 * 1024 * 1024
private const val MAX_QR_PIXELS = 1024

private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    var sample = 1
    while (longest / sample > maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun openAppSettings(activity: Activity) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", activity.packageName, null),
    )
    runCatching { activity.startActivity(intent) }
}
