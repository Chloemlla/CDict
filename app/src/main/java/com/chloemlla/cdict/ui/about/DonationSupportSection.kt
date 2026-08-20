package com.chloemlla.cdict.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chloemlla.cdict.core.net.DonationClaimOutcome
import com.chloemlla.cdict.core.net.DonationClient
import kotlinx.coroutines.launch

private data class ClaimFeedback(val message: String, val positive: Boolean)

/**
 * 赞赏页底部的「署名鸣谢」区块：署名说明、署名申请表单与实时名单。
 *
 * 名单跟渠道列表一起由服务端下发，开发者核实转账备注后应用内即时可见，不需要发版。
 * 提交前先过一遍本地限流（[DonationClaimQuota]），误触连点不会白白打到后端。
 */
@Composable
fun DonationSupportSection(
    client: DonationClient,
    supporters: List<String>,
    onCelebrate: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { AboutStore(context) }
    val scope = rememberCoroutineScope()
    var transactionId by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ClaimFeedback?>(null) }

    val submit: () -> Unit = submit@{
        if (submitting) return@submit
        val now = System.currentTimeMillis()
        val quota = store.donationClaimQuota
        val blocked = quota.rejectionAt(now)
        if (blocked != null) {
            feedback = ClaimFeedback(blocked, false)
            return@submit
        }
        store.donationClaimQuota = quota.accepted(now)
        submitting = true
        val submittedId = transactionId
        val submittedName = displayName
        scope.launch {
            val outcome = client.submitClaim(submittedId, submittedName)
            submitting = false
            when (outcome) {
                is DonationClaimOutcome.Accepted -> {
                    feedback = ClaimFeedback(outcome.message, true)
                    transactionId = ""
                    displayName = ""
                    onCelebrate()
                }
                is DonationClaimOutcome.Rejected -> feedback = ClaimFeedback(outcome.message, false)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SupporterHintCard()
        Spacer(Modifier.height(16.dp))
        ClaimForm(
            transactionId = transactionId,
            displayName = displayName,
            submitting = submitting,
            feedback = feedback,
            onTransactionIdChange = { transactionId = it.trim().take(64) },
            onDisplayNameChange = { displayName = it.take(32) },
            onSubmit = submit,
        )
        Spacer(Modifier.height(20.dp))
        SupporterRoll(supporters)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SupporterHintCard() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            scheme.primaryContainer.copy(alpha = 0.55f),
                            scheme.tertiaryContainer.copy(alpha = 0.30f),
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(scheme.primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "署名鸣谢",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "赞赏可以署名，也可以完全匿名",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Column {
                    Text(
                        SUPPORTER_HINT_HEADLINE,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    SUPPORTER_HINT_LINES.forEach { line ->
                        Spacer(Modifier.height(10.dp))
                        HintBullet(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun HintBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClaimForm(
    transactionId: String,
    displayName: String,
    submitting: Boolean,
    feedback: ClaimFeedback?,
    onTransactionIdChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val canSubmit = !submitting && transactionId.trim().length >= 6 && displayName.isNotBlank()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "申请加入鸣谢名单",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "不方便写备注也没关系：填下转账的交易号和想展示的称呼，开发者核实后加入名单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = transactionId,
                onValueChange = onTransactionIdChange,
                label = { Text("交易号 / 订单号") },
                placeholder = { Text("支付宝或微信账单详情里的交易单号") },
                supportingText = { Text("6-64 位，只含字母、数字、连字符或下划线") },
                singleLine = true,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("希望展示的称呼") },
                placeholder = { Text("名单里显示的名字") },
                supportingText = { Text("${displayName.length}/32，会公开展示，别填真实隐私信息") },
                singleLine = true,
                enabled = !submitting,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (submitting) "正在提交…" else "提交署名申请")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "只上传交易号与称呼两项，不采集设备信息；同一交易号重复提交不会重复排队。" +
                    "为防误触，两次提交至少间隔 ${DonationClaimQuota.MIN_INTERVAL_MILLIS / 1000} 秒，" +
                    "一小时内最多 ${DonationClaimQuota.MAX_PER_WINDOW} 次。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            AnimatedVisibility(visible = feedback != null) {
                val current = feedback ?: return@AnimatedVisibility
                Column {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (current.positive) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ) {
                        Text(
                            current.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (current.positive) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupporterRoll(supporters: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Celebration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "鸣谢名单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (supporters.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        supporters.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SelectionContainer {
            SupporterNameFlow(supporters)
        }
    }
}

/** 名单本身：赞赏页与开源许可声明页共用同一份渲染，外层负责套 SelectionContainer。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupporterNameFlow(supporters: List<String>, modifier: Modifier = Modifier) {
    if (supporters.isEmpty()) {
        Text(
            "名单暂时是空的，你可以是第一位。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        supporters.forEach { name ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private const val SUPPORTER_HINT_HEADLINE =
    "您的名字会在开发者核实您的转账备注之后，实时更新在软件内置的鸣谢名单中。"

private val SUPPORTER_HINT_LINES = listOf(
    "两端都支持署名：支付宝、微信任一渠道赞赏都可以署名。",
    "转账时在备注里写想展示的称呼，或者用下面的表单填交易号申请。",
    "核实通过后名单随服务端实时更新，应用内即时可见，不用等版本更新。",
    "不写备注、不提交申请就是匿名支持，同样感谢。",
)
