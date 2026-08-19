package com.chloemlla.cdict.ui.about

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chloemlla.cdict.core.update.UpdateChecker
import com.chloemlla.cdict.core.update.UpdateDialog
import com.chloemlla.cdict.core.update.UpdateDialogState
import com.chloemlla.cdict.core.update.UpdateInstaller
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
                        it.message ?: "无法启动安装器",
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
                        throwable.message ?: "检查更新失败",
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
        coroutineScope.launch {
            downloadingUpdate = true
            updateDialogState = UpdateDialogState.Downloading(candidate, asset)
            downloadProgressBytes = 0L
            downloadProgressTotalBytes = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    updateInstaller.downloadApk(asset) { downloadedBytes, totalBytes ->
                        downloadProgressBytes = downloadedBytes
                        downloadProgressTotalBytes = totalBytes
                    }
                }
            }
            downloadingUpdate = false
            downloadProgressBytes = 0L
            downloadProgressTotalBytes = null
            result.onSuccess { file ->
                startInstallIfAllowed(candidate, file)
            }.onFailure { throwable ->
                updateDialogState = UpdateDialogState.Error(
                    throwable.message ?: "下载更新失败",
                )
            }
        }
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
        if (BuildInfo.updateCheckEnabled && !BuildInfo.isDevBuild && !autoCheckStarted) {
            triggerUpdateCheck(manual = false)
        }
    }

    val top = controller.current
    if (top?.forced == true) {
        BackHandler { /* 强制页：拦截系统返回键 */ }
    } else {
        BackHandler(enabled = controller.isOpen) { controller.pop() }
    }

    CompositionLocalProvider(LocalAboutController provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            val current = controller.current
            if (current != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
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
            UpdateDialog(
                state = updateDialogState,
                downloadingUpdate = downloadingUpdate,
                downloadProgressBytes = downloadProgressBytes,
                downloadProgressTotalBytes = downloadProgressTotalBytes,
                onDismiss = { updateDialogState = UpdateDialogState.Hidden },
                onDownloadUpdate = { candidate, asset -> triggerUpdateDownload(candidate, asset) },
                onInstallDownloadedApk = { candidate, file -> startInstallIfAllowed(candidate, file) },
                onError = { message -> updateDialogState = UpdateDialogState.Error(message) },
                updateInstaller = updateInstaller,
            )
        }
    }
}
