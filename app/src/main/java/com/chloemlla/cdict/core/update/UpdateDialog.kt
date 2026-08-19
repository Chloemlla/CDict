package com.chloemlla.cdict.core.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.ui.about.BuildInfo
import com.chloemlla.cdict.ui.about.UrlOpener
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

sealed interface UpdateDialogState {
    data object Hidden : UpdateDialogState
    data object Checking : UpdateDialogState
    data class UpdateAvailable(val candidate: UpdateCandidate) : UpdateDialogState
    data object NoUpdate : UpdateDialogState
    data class Downloading(val candidate: UpdateCandidate, val asset: ReleaseAsset) : UpdateDialogState
    data class InstallAuthorization(val candidate: UpdateCandidate, val file: File) : UpdateDialogState
    data class Error(val message: String) : UpdateDialogState
}

private val updateDialogTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes / (1024L * 1024L)} MB"
}

@Composable
fun UpdateDialog(
    state: UpdateDialogState,
    downloadingUpdate: Boolean,
    downloadProgressBytes: Long,
    downloadProgressTotalBytes: Long?,
    onDismiss: () -> Unit,
    onDownloadUpdate: (UpdateCandidate, ReleaseAsset) -> Unit,
    onInstallDownloadedApk: (UpdateCandidate, File) -> Unit,
    onError: (String) -> Unit,
    updateInstaller: UpdateInstaller,
) {
    val context = LocalContext.current

    when (val currentState = state) {
        UpdateDialogState.Hidden -> Unit
        UpdateDialogState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text("检查更新") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("正在检查最新版本…")
                }
            },
            confirmButton = {},
        )
        UpdateDialogState.NoUpdate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已是最新版本") },
            text = { Text("当前版本已是最新，无需更新。") },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text("确定") }
            },
        )
        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查失败") },
            text = { Text(currentState.message) },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text("确定") }
            },
        )
        is UpdateDialogState.UpdateAvailable -> {
            val candidate = currentState.candidate
            val release = candidate.release
            val targetAsset = candidate.matchedAsset
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(release.tagName) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("发现新版本：${release.tagName}")
                        Text("当前版本：${BuildInfo.versionName} (${BuildInfo.shortHash})")
                        val publishTime = Instant.ofEpochMilli(release.publishedAtUtcMillis)
                            .atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
                        Text("发布时间：$publishTime")
                        targetAsset?.let { asset ->
                            val size = asset.sizeBytes?.let(::formatUpdateBytes)
                            Text("下载包：${asset.name}${size?.let { "（$it）" } ?: ""}")
                        }
                        if (release.body.isNotBlank()) {
                            Text(
                                text = release.body,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                confirmButton = {
                    OutlinedButton(
                        enabled = !downloadingUpdate,
                        onClick = {
                            if (targetAsset != null) {
                                onDownloadUpdate(candidate, targetAsset)
                            } else if (release.htmlUrl.isNotBlank()) {
                                UrlOpener.open(context, release.htmlUrl)
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Text(
                            if (targetAsset != null) "下载更新" else "查看发布页",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                },
            )
        }
        is UpdateDialogState.Downloading -> {
            val release = currentState.candidate.release
            AlertDialog(
                onDismissRequest = {},
                title = { Text("正在下载更新") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("正在下载 ${release.tagName}…")
                        val progress = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
                            downloadProgressBytes.toFloat() / it.toFloat()
                        }
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
                                val percent = ((downloadProgressBytes * 100) / it).coerceIn(0, 100)
                                "$percent%（${formatUpdateBytes(downloadProgressBytes)} / ${formatUpdateBytes(it)}）"
                            } ?: "正在连接并获取下载大小…",
                        )
                        Text("下载完成后将提示安装，请保持应用打开。")
                    }
                },
                confirmButton = {},
                dismissButton = {},
            )
        }
        is UpdateDialogState.InstallAuthorization -> {
            val candidate = currentState.candidate
            val release = candidate.release
            val permissionGranted = updateInstaller.canInstallPackages()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("安装更新") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("需要授权安装未知来源应用才能完成更新安装。")
                        Text("新版本：${release.tagName}")
                    }
                },
                confirmButton = {
                    OutlinedButton(onClick = {
                        if (permissionGranted) {
                            onInstallDownloadedApk(candidate, currentState.file)
                        } else {
                            runCatching { context.startActivity(updateInstaller.createInstallPermissionIntent()) }
                                .onFailure {
                                    onError(it.message ?: "无法打开安装设置页面")
                                }
                        }
                    }) {
                        Text(if (permissionGranted) "立即安装" else "去授权")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                },
            )
        }
    }
}
