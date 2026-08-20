package com.chloemlla.cdict.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val CdictLightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF3559A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF00194B),
    secondary = Color(0xFF5A5F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE2F9),
    onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF76536D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F0),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFB0C6FF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val CdictDarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002C6B),
    primaryContainer = Color(0xFF194488),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC1C5DD),
    onSecondary = Color(0xFF2A2F42),
    secondaryContainer = Color(0xFF42475A),
    onSecondaryContainer = Color(0xFFDEE2F9),
    tertiary = Color(0xFFE9B9D8),
    onTertiary = Color(0xFF44263D),
    tertiaryContainer = Color(0xFF5C3B54),
    onTertiaryContainer = Color(0xFFFFD7F0),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF3559A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// 词典释义、例句多为中英混排长文本，正文改用段落级断行，减少中文标点行首悬挂与英文单词
// 被硬切；标题使用 Heading 断行策略，短标题不会在不合适的位置折行。
private val CdictTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, lineBreak = LineBreak.Heading),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, lineBreak = LineBreak.Heading),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineBreak = LineBreak.Paragraph),
        bodyMedium = bodyMedium.copy(lineBreak = LineBreak.Paragraph),
        bodySmall = bodySmall.copy(lineBreak = LineBreak.Paragraph),
    )
}

private val CdictShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun CdictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamicScheme = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            // 动态色保留系统中性色，同时锁定品牌主色，避免壁纸改变核心操作语义。
            dynamicScheme.copy(
                primary = if (darkTheme) CdictDarkColorScheme.primary else CdictLightColorScheme.primary,
                onPrimary = if (darkTheme) CdictDarkColorScheme.onPrimary else CdictLightColorScheme.onPrimary,
                primaryContainer = if (darkTheme) {
                    CdictDarkColorScheme.primaryContainer
                } else {
                    CdictLightColorScheme.primaryContainer
                },
                onPrimaryContainer = if (darkTheme) {
                    CdictDarkColorScheme.onPrimaryContainer
                } else {
                    CdictLightColorScheme.onPrimaryContainer
                },
                inversePrimary = if (darkTheme) {
                    CdictDarkColorScheme.inversePrimary
                } else {
                    CdictLightColorScheme.inversePrimary
                },
            )
        }
        darkTheme -> CdictDarkColorScheme
        else -> CdictLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CdictTypography,
        shapes = CdictShapes,
        content = content,
    )
}
