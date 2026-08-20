package com.chloemlla.cdict.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chloemlla.cdict.core.update.UpdateChecker
import com.chloemlla.cdict.core.update.UpdateDialog
import com.chloemlla.cdict.core.update.UpdateDialogState
import com.chloemlla.cdict.core.update.UpdateInstaller
import com.chloemlla.cdict.ui.ResponsiveContentBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AboutOverlayHost(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val controller = remember { AboutController() }
    val store = remember { AboutStore(context) }
    val coroutineScope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker(context) }
    val updateInstaller = remember { UpdateInstaller(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var updateDialogState by remember { mutableStateOf<UpdateDialogState>(UpdateDialogState.Hidden) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressBytes by remember { mutableLongStateOf(0L) }
    var downloadProgressTotalBytes by remember { mutableStateOf<Long?>(null) }
    var autoCheckStarted by remember { mutableStateOf(false) }
    var updateCheckJob by remember { mutableStateOf<Job?>(null) }
    var updateDownloadJob by remember { mutableStateOf<Job?>(null) }

    fun maybeShowWhatsNew() {
        if (!BuildInfo.isDevBuild &&
            (store.acknowledgedCommitHash != BuildInfo.commitHash ||
                store.acknowledgedBuildTime != BuildInfo.buildTimeSeconds)
        ) {
            controller.push(
                route = AboutScreenRoute.WhatsNew,
                forced = true,
                onFinished = {
                    store.acknowledgedCommitHash = BuildInfo.commitHash
                    store.acknowledgedBuildTime = BuildInfo.buildTimeSeconds
                },
            )
        }
    }

    fun startInstallIfAllowed(candidate: com.chloemlla.cdict.core.update.UpdateCandidate, file: File) {
        if (updateInstaller.canInstallPackages()) {
            runCatching { updateInstaller.installApk(file) }
                .onSuccess { updateDialogState = UpdateDialogState.Hidden }
                .onFailure {
                    updateDialogState = UpdateDialogState.Error(
                        detail = it.message.orEmpty(),
                        title = "无法启动安装程序",
                    )
                }
            return
        }
        updateDialogState = UpdateDialogState.InstallAuthorization(candidate, file)
    }

    fun triggerUpdateCheck(manual: Boolean) {
        if (!BuildInfo.updateCheckEnabled) return

        updateCheckJob?.cancel()
        updateCheckJob = coroutineScope.launch {
            if (manual) {
                updateDialogState = UpdateDialogState.Checking
            }
            try {
                val candidate = withContext(Dispatchers.IO) {
                    updateChecker.checkForUpdate()
                }
                updateDialogState = when {
                    candidate != null -> UpdateDialogState.UpdateAvailable(candidate)
                    manual -> UpdateDialogState.NoUpdate
                    else -> UpdateDialogState.Hidden
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                if (manual) {
                    updateDialogState = UpdateDialogState.Error(
                        detail = throwable.message.orEmpty(),
                        title = "检查更新失败",
                    )
                }
            } finally {
                autoCheckStarted = true
            }
        }
    }

    fun triggerUpdateDownload(
        candidate: com.chloemlla.cdict.core.update.UpdateCandidate,
        asset: com.chloemlla.cdict.core.update.ReleaseAsset,
    ) {
        updateDownloadJob?.cancel()
        updateDownloadJob = coroutineScope.launch {
            downloadingUpdate = true
            updateDialogState = UpdateDialogState.Downloading(candidate, asset)
            downloadProgressBytes = 0L
            downloadProgressTotalBytes = null
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        updateInstaller.downloadApk(asset) { downloadedBytes, totalBytes ->
                            downloadProgressBytes = downloadedBytes
                            downloadProgressTotalBytes = totalBytes
                        }
                    }
                }
                result.onSuccess { file ->
                    startInstallIfAllowed(candidate, file)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    updateDialogState = UpdateDialogState.Error(
                        detail = throwable.message.orEmpty(),
                        title = "下载更新失败",
                    )
                }
            } finally {
                downloadingUpdate = false
                downloadProgressBytes = 0L
                downloadProgressTotalBytes = null
            }
        }
    }

    // 取消下载：立即收起对话框；底层阻塞读取无法中断，因此保留 downloadingUpdate=true
    // 直到协程真正结束，避免并发写入同一个缓存 APK 文件。
    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDialogState = UpdateDialogState.Hidden
    }

    DisposableEffect(lifecycleOwner, updateDialogState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val pendingInstall = updateDialogState as? UpdateDialogState.InstallAuthorization
                ?: return@LifecycleEventObserver
            if (updateInstaller.canInstallPackages()) {
                startInstallIfAllowed(pendingInstall.candidate, pendingInstall.file)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!store.ossNoticeSeen) {
            controller.push(
                route = AboutScreenRoute.OssNotice,
                forced = true,
                onFinished = {
                    store.ossNoticeSeen = true
                    maybeShowWhatsNew()
                },
            )
        } else {
            maybeShowWhatsNew()
        }
    }

    LaunchedEffect(Unit) {
        if (BuildInfo.updateCheckEnabled &&
            !BuildInfo.isDevBuild &&
            !autoCheckStarted &&
            store.autoCheckUpdate
        ) {
            triggerUpdateCheck(manual = false)
        }
    }

    val top = controller.current
    if (top?.forced == true) {
        // 强制浮层（如首次启动的开源声明）必须点按钮确认，这里吞掉返回键。
        BackHandler {}
    } else {
        BackHandler(enabled = controller.isOpen) { controller.pop() }
    }

    CompositionLocalProvider(LocalAboutController provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedContent(
                targetState = controller.current,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "about-overlay",
            ) { current ->
                if (current != null) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val scrimInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                            .clickable(
                                enabled = !current.forced,
                                interactionSource = scrimInteractionSource,
                                indication = null,
                                onClickLabel = "关闭",
                                onClick = controller::pop,
                            ),
                    ) {
                        ResponsiveContentBox(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = {},
                                    )
                                    .semantics {
                                        paneTitle = when (current.route) {
                                            AboutScreenRoute.About -> "关于"
                                            AboutScreenRoute.Declarations -> "应用声明"
                                            AboutScreenRoute.LegalInfo -> "法律信息"
                                            AboutScreenRoute.OssNotice -> "开源许可声明"
                                            AboutScreenRoute.AppPermissions -> "应用权限"
                                            AboutScreenRoute.WhatsNew -> "本次更新说明"
                                        }
                                    },
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                when (current.route) {
                                    AboutScreenRoute.About -> AboutScreen(
                                        onBack = controller::pop,
                                        updateCheckEnabled = BuildInfo.updateCheckEnabled,
                                        updateCheckInProgress = updateDialogState is UpdateDialogState.Checking,
                                        onCheckForUpdate = { triggerUpdateCheck(manual = true) },
                                    )
                                    AboutScreenRoute.Declarations -> DeclarationsScreen(onBack = controller::pop)
                                    AboutScreenRoute.LegalInfo -> LegalInfoScreen(onBack = controller::pop)
                                    AboutScreenRoute.OssNotice -> OssNoticeScreen(
                                        forced = current.forced,
                                        onBack = controller::pop,
                                    )
                                    AboutScreenRoute.AppPermissions -> AppPermissionsScreen(onBack = controller::pop)
                                    AboutScreenRoute.WhatsNew -> WhatsNewScreen(
                                        forced = current.forced,
                                        onBack = controller::pop,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            UpdateDialog(
                state = updateDialogState,
                downloadingUpdate = downloadingUpdate,
                downloadProgressBytes = downloadProgressBytes,
                downloadProgressTotalBytes = downloadProgressTotalBytes,
                onDismiss = {
                    updateCheckJob?.cancel()
                    updateDialogState = UpdateDialogState.Hidden
                },
                onDownloadUpdate = { candidate, asset -> triggerUpdateDownload(candidate, asset) },
                onInstallDownloadedApk = { candidate, file -> startInstallIfAllowed(candidate, file) },
                onError = { message ->
                    updateDialogState = UpdateDialogState.Error(
                        detail = message,
                        title = "无法打开授权页面",
                    )
                },
                updateInstaller = updateInstaller,
                onCancelDownload = { cancelUpdateDownload() },
            )
        }
    }
}
