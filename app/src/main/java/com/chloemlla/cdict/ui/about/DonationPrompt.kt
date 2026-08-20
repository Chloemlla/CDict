package com.chloemlla.cdict.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 触发赞赏提示所需的累计前台时长。 */
private const val FOREGROUND_THRESHOLD_MILLIS = 30L * 60L * 1000L

/**
 * 赞赏提示的出现时机：累计前台使用 ≥ 30 分钟且完整做完过至少一轮复习，才在复习小结页显示一次
 * 入口与一条底部提示条；用户以任何方式关掉（点入口或点关闭）后都不再自动出现。
 *
 * 判定完全离线：只读 [AboutStore] 里的本地计数，不联网、不埋点、不需要新权限。划词弹窗那条路径
 * 不走 MainActivity，因此既不累加时长也不会触发提示。
 */
object DonationPromptGate {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** 回到主标签时调用一次：满足条件就点亮提示，并记下"已出现过"。 */
    fun evaluate(store: AboutStore) {
        if (_visible.value) return
        if (store.tipPromptDismissed || store.tipPromptShown) return
        if (!store.reviewRoundDone) return
        if (store.foregroundMillis < FOREGROUND_THRESHOLD_MILLIS) return
        store.tipPromptShown = true
        _visible.value = true
    }

    /** 关掉提示（点关闭或点入口都算），此后永不自动出现。 */
    fun dismiss(store: AboutStore) {
        store.tipPromptDismissed = true
        _visible.value = false
    }
}

/** 底部提示条：一句话说明 + 「看看」入口 + 关闭按钮，纯跳转，不解锁任何功能。 */
@Composable
fun DonationTipBar(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "用了一段时间了，觉得还行的话可以赞赏一下；应用永久免费，赞赏不解锁任何功能。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpen) { Text("看看") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭赞赏提示")
            }
        }
    }
}
