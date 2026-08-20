package com.chloemlla.cdict.quicktranslate

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.cdict.MainActivity
import com.chloemlla.cdict.core.data.QuickWord
import com.chloemlla.cdict.ui.CdictTheme

/**
 * 系统文本选择工具条（复制 / 全选 那一条）里的「CDict 翻译」入口。
 *
 * 注册 PROCESS_TEXT / TRANSLATE / DEFINE 三个动作，落在一个透明窗口上，只从底部升起
 * 翻译弹窗，不打开主界面；词库命中时弹窗内的「前往」才把用户带进 CDict 的词条详情。
 */
class QuickTranslateActivity : ComponentActivity() {
    private val viewModel: QuickTranslateViewModel by viewModels {
        QuickTranslateViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = selectedText(intent)
        if (selected == null) {
            Toast.makeText(this, "没有可翻译的选中文本", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        enableEdgeToEdge()
        viewModel.start(selected)
        setContent {
            CdictTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                QuickTranslateSheet(
                    state = state,
                    onRetry = viewModel::retry,
                    onToggleDirection = viewModel::toggleDirection,
                    onOpenEntry = ::openEntryInApp,
                    onDismiss = ::finish,
                )
            }
        }
    }

    /**
     * 带着词条跳进主界面。用 NEW_TASK + CLEAR_TOP + SINGLE_TOP：弹窗自己在独立任务里，
     * 已在后台的 CDict 会被拉回前台并走 onNewIntent，而不是叠出第二个主界面。
     */
    private fun openEntryInApp(entry: QuickWord) {
        val target = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_WORD, entry.word)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        startActivity(target)
        finish()
    }

    companion object {
        /**
         * PROCESS_TEXT 走 EXTRA_PROCESS_TEXT，TRANSLATE / DEFINE 走 EXTRA_TEXT，
         * 少数发起方只放 ClipData，因此三处依次兜底。空白选区返回 null。
         */
        fun selectedText(intent: Intent?): String? {
            if (intent == null) return null
            val extra = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            val raw = extra?.toString()
                ?: intent.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
            return raw?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
