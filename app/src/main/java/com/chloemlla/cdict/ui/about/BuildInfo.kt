package com.chloemlla.cdict.ui.about

import com.chloemlla.cdict.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BuildInfo {
    val commitHash: String = BuildConfig.COMMIT_HASH.trim().ifBlank { "N/A" }
    val buildTimeSeconds: Long = BuildConfig.BUILD_TIME
    val versionLabel: String = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
    val isDevBuild: Boolean = commitHash == "N/A" || buildTimeSeconds <= 0

    fun formatBuildTime(): String =
        if (buildTimeSeconds <= 0) {
            "N/A"
        } else {
            Instant.ofEpochSecond(buildTimeSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
}
