package com.chloemlla.cdict.ui.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 26
private const val BURST_DURATION_MILLIS = 2600

private val CONFETTI_EMOJIS = listOf("🎉", "🎊", "✨", "💛")

private data class ConfettiParticle(
    val emoji: String,
    val startX: Float,
    val delay: Float,
    val spin: Float,
    val scale: Float,
    val drift: Float,
)

/**
 * 一次性的 🎉 洒落动画：[burstKey] 每次自增就重新洒一遍，为 0 时不渲染任何东西。
 *
 * 纯 Compose 动画，不引入额外依赖；覆盖层不拦触摸，也对读屏软件隐藏。
 */
@Composable
fun EmojiConfetti(burstKey: Int, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    if (burstKey <= 0) return
    val finished = rememberUpdatedState(onFinished)
    val particles = remember(burstKey) {
        val random = Random(burstKey * 31L + 7L)
        List(PARTICLE_COUNT) {
            ConfettiParticle(
                emoji = CONFETTI_EMOJIS[random.nextInt(CONFETTI_EMOJIS.size)],
                startX = 0.02f + random.nextFloat() * 0.9f,
                delay = random.nextFloat() * 0.35f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                scale = 0.75f + random.nextFloat() * 0.75f,
                drift = (random.nextFloat() - 0.5f) * 0.24f,
            )
        }
    }
    val progress = remember(burstKey) { Animatable(0f) }
    LaunchedEffect(burstKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = BURST_DURATION_MILLIS, easing = LinearEasing))
        finished.value()
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {},
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val travelPx = with(density) { (maxHeight + 96.dp).toPx() }
        val liftPx = with(density) { 48.dp.toPx() }
        particles.forEach { particle ->
            Text(
                text = particle.emoji,
                fontSize = 26.sp,
                modifier = Modifier
                    .offset {
                        val local = particle.localProgress(progress.value)
                        val sway = widthPx * particle.drift * sin(local * 2f * PI.toFloat())
                        IntOffset(
                            (widthPx * particle.startX + sway).roundToInt(),
                            (travelPx * local - liftPx).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        val local = particle.localProgress(progress.value)
                        rotationZ = particle.spin * local
                        scaleX = particle.scale
                        scaleY = particle.scale
                        alpha = when {
                            local <= 0f -> 0f
                            local > 0.8f -> ((1f - local) / 0.2f).coerceIn(0f, 1f)
                            else -> 1f
                        }
                    },
            )
        }
    }
}

private fun ConfettiParticle.localProgress(progress: Float): Float =
    ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
