package com.chloemlla.cdict.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

object UrlOpener {
    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_LONG).show()
        }
    }

    fun copy(context: Context, text: String, toastText: String? = null) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(context, toastText ?: "已复制", Toast.LENGTH_SHORT).show()
    }
}
