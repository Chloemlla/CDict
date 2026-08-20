package com.chloemlla.cdict.ui.about

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

enum class AboutScreenRoute {
    About, Declarations, LegalInfo, OssNotice, AppPermissions, WhatsNew, Donation
}

class AboutController {
    data class Entry(
        val route: AboutScreenRoute,
        val forced: Boolean,
        val onFinished: (() -> Unit)?,
    )

    private val _stack = mutableStateListOf<Entry>()

    val stack: List<Entry> get() = _stack
    val current: Entry? get() = _stack.lastOrNull()
    val isOpen: Boolean get() = _stack.isNotEmpty()

    fun push(route: AboutScreenRoute, forced: Boolean = false, onFinished: (() -> Unit)? = null) {
        _stack.add(Entry(route, forced, onFinished))
    }

    fun pop() {
        if (_stack.isEmpty()) return
        val top = _stack.removeAt(_stack.lastIndex)
        top.onFinished?.invoke()
    }
}

val LocalAboutController = staticCompositionLocalOf<AboutController> {
    error("No AboutController in composition")
}
