package com.chloemlla.cdict.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun AboutOverlayHost(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val controller = remember { AboutController() }
    val store = remember { AboutStore(context) }

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
                        AboutScreenRoute.About -> AboutScreen(onBack = controller::pop)
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
