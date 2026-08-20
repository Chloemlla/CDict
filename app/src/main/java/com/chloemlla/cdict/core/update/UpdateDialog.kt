package com.chloemlla.cdict.core.update

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.ui.about.BuildInfo
import com.chloemlla.cdict.ui.about.UrlOpener
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val updateDialogTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes / (1024L * 1024L)} MB"
}

sealed interface UpdateDialogState {
    data object Hidden : UpdateDialogState
    data object Checking : UpdateDialogState
    data class UpdateAvailable(val candidate: UpdateCandidate) : UpdateDialogState
    data object NoUpdate : UpdateDialogState
    data class Downloading(val candidate: UpdateCandidate, val asset: ReleaseAsset) : UpdateDialogState
    data class InstallAuthorization(val candidate: UpdateCandidate, val file: File) : UpdateDialogState
    data class Error(val message: String) : UpdateDialogState
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
        UpdateDialogState.Checking -> UpdateCheckingDialog(onDismiss = onDismiss)
        UpdateDialogState.NoUpdate -> UpdateNoUpdateDialog(onDismiss = onDismiss)
        is UpdateDialogState.Error -> UpdateErrorDialog(
            message = currentState.message,
            onDismiss = onDismiss,
        )
        is UpdateDialogState.UpdateAvailable -> UpdateAvailableDialog(
            candidate = currentState.candidate,
            downloadingUpdate = downloadingUpdate,
            onDismiss = onDismiss,
            onDownloadUpdate = onDownloadUpdate,
            context = context,
        )
        is UpdateDialogState.Downloading -> UpdateDownloadingDialog(
            candidate = currentState.candidate,
            downloadProgressBytes = downloadProgressBytes,
            downloadProgressTotalBytes = downloadProgressTotalBytes,
        )
        is UpdateDialogState.InstallAuthorization -> UpdateInstallAuthDialog(
            candidate = currentState.candidate,
            file = currentState.file,
            updateInstaller = updateInstaller,
            onDismiss = onDismiss,
            onInstallDownloadedApk = onInstallDownloadedApk,
            onError = onError,
            context = context,
        )
    }
}

@Composable
private fun UpdateCheckingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            DialogIcon(
                icon = Icons.Filled.SystemUpdate,
                tint = MaterialTheme.colorScheme.primary,
                animated = true,
            )
        },
        title = {
            Text(
                text = "检查更新",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "正在检查最新版本…",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun UpdateNoUpdateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(icon = Icons.Filled.CheckCircle, tint = MaterialTheme.colorScheme.tertiary) },
        title = {
            Text(
                text = "已是最新版本",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "无需更新，您已在使用最新功能。",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                SelectionContainer {
                    Text(
                        text = "当前版本：${BuildInfo.versionLabel}",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) { Text("确定", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {},
    )
}

@Composable
private fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(icon = Icons.Filled.Warning, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text(
                text = "检查失败",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SelectionContainer {
                    Text(
                        text = message,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) { Text("关闭", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {},
    )
}

@Composable
private fun UpdateAvailableDialog(
    candidate: UpdateCandidate,
    downloadingUpdate: Boolean,
    onDismiss: () -> Unit,
    onDownloadUpdate: (UpdateCandidate, ReleaseAsset) -> Unit,
    context: android.content.Context,
) {
    val release = candidate.release
    val asset = candidate.matchedAsset
    val isNewer = candidate.isTimeFallback
    val displayVersion = release.tagName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DialogIcon(icon = Icons.Filled.SystemUpdate, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        text = "发现新版本${if (isNewer) "（按发布时间匹配）" else ""}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = asset?.sizeBytes?.let { "$displayVersion · ${formatUpdateBytes(it)}" } ?: displayVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ReleaseInfoSection(release = release, asset = asset)
                if (release.body.isNotBlank()) {
                    ReleaseNotesSection(body = release.body)
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (asset != null) {
                    FilledTonalButton(
                        onClick = { onDownloadUpdate(candidate, asset) },
                        enabled = !downloadingUpdate,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("下载更新", fontWeight = FontWeight.SemiBold)
                    }
                } else if (release.htmlUrl.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { UrlOpener.open(context, release.htmlUrl) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("查看发布页", fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("稍后", fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun ReleaseInfoSection(release: ReleaseInfo, asset: ReleaseAsset?) {
    val publishTime = Instant.ofEpochMilli(release.publishedAtUtcMillis)
        .atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(
                    label = "新版本",
                    value = release.tagName,
                    icon = Icons.Filled.Info,
                    modifier = Modifier.weight(1f),
                )
                InfoRow(
                    label = "当前版本",
                    value = BuildInfo.versionLabel,
                    icon = Icons.Filled.SystemUpdate,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(
                    label = "发布时间",
                    value = publishTime,
                    icon = Icons.Filled.Info,
                    modifier = Modifier.weight(1f),
                )
                if (asset != null) {
                    InfoRow(
                        label = "下载包",
                        value = asset.sizeBytes?.let { formatUpdateBytes(it) } ?: asset.name,
                        icon = Icons.Filled.FileDownload,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReleaseNotesSection(body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "更新内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UpdateDownloadingDialog(
    candidate: UpdateCandidate,
    downloadProgressBytes: Long,
    downloadProgressTotalBytes: Long?,
) {
    val progress = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
        (downloadProgressBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f)
    }
    val progressPercent = progress?.let { (it * 100).roundToInt() } ?: 0
    val downloadedStr = formatUpdateBytes(downloadProgressBytes)
    val totalStr = downloadProgressTotalBytes?.let { formatUpdateBytes(it) } ?: "未知大小"

    AlertDialog(
        onDismissRequest = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DialogIcon(icon = Icons.Filled.FileDownload, tint = MaterialTheme.colorScheme.primary, animated = true)
                Column {
                    Text(text = "正在下载更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(text = candidate.release.tagName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = "$progressPercent%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (progress != null) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                    Text(
                        text = "$downloadedStr / $totalStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (progress != null) "下载完成后将提示安装，请保持应用打开。" else "正在连接并获取下载大小…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun UpdateInstallAuthDialog(
    candidate: UpdateCandidate,
    file: File,
    updateInstaller: UpdateInstaller,
    onDismiss: () -> Unit,
    onInstallDownloadedApk: (UpdateCandidate, File) -> Unit,
    onError: (String) -> Unit,
    context: android.content.Context,
) {
    val release = candidate.release
    val permissionGranted = updateInstaller.canInstallPackages()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(icon = Icons.Filled.SystemUpdate, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                text = "安装更新",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "新版本：${release.tagName}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shape = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "需要授权安装未知来源应用才能完成更新安装。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (permissionGranted) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "当前已有安装权限，可直接安装。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (permissionGranted) {
                            onInstallDownloadedApk(candidate, file)
                        } else {
                            try {
                                context.startActivity(updateInstaller.createInstallPermissionIntent())
                            } catch (e: Exception) {
                                onError(e.message ?: "无法打开安装设置页面")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (permissionGranted) "立即安装" else "去授权", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("取消", fontWeight = FontWeight.SemiBold) }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun DialogIcon(icon: ImageVector, tint: Color, animated: Boolean = false) {
    val pulse = if (animated) rememberPulseScale() else null
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(
                if (pulse != null) {
                    Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                } else {
                    Modifier
                }
            )
            .clip(MaterialTheme.shapes.large)
            .background(tint.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.3f)), MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun rememberPulseScale(): State<Float> {
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "dialogIcon")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
}
