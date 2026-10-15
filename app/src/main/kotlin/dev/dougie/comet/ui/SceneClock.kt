package dev.dougie.comet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

/**
 * Per-frame animation state, integrated over time so changing speed never makes anything jump.
 * Fields are read only inside draw blocks, so animating does not recompose.
 */
/** One flight of the comet: a straight line through the scene, in screen fractions. */
class CometPass(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float, val scale: Float) {
    companion object {
        fun random(): CometPass {
            // A line through a random point at a random angle, long enough to start and end off-screen.
            val cx = 0.1f + 0.8f * Random.nextFloat()
            val cy = 0.1f + 0.8f * Random.nextFloat()
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val dx = cos(angle) * 1.2f
            val dy = sin(angle) * 1.2f
            return CometPass(cx - dx, cy - dy, cx + dx, cy + dy, 0.5f + 0.3f * Random.nextFloat())
        }
    }
}

class SceneClock {
    val comet = mutableStateOf(CometPass.random())
    val planetSpin = mutableFloatStateOf(0f)
    val starScroll = mutableFloatStateOf(0f)
    val cometProgress = mutableFloatStateOf(0f)
    val orbit = arrayOf(mutableFloatStateOf(0.4f), mutableFloatStateOf(2.5f), mutableFloatStateOf(4.6f))
}

/** [energy] is 0 when idle and grows (roughly logarithmically) with live Mbps while testing. */
@Composable
fun rememberSceneClock(energy: Float, activeMoon: Int): SceneClock {
    val clock = remember { SceneClock() }
    val energyNow by rememberUpdatedState(energy)
    val moonNow by rememberUpdatedState(activeMoon)
    LaunchedEffect(clock) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                val e = energyNow
                clock.planetSpin.floatValue += dt * (0.25f + 0.9f * e)
                clock.starScroll.floatValue += dt * (0.015f + 0.12f * e)
                val p = clock.cometProgress.floatValue + dt * (0.1f + 0.2f * e)
                if (p >= 1.15f) {
                    clock.comet.value = CometPass.random()
                    clock.cometProgress.floatValue = 0f
                } else {
                    clock.cometProgress.floatValue = p
                }
                for (i in 0..2) {
                    val boost = if (i == moonNow) 2.2f else 1f
                    clock.orbit[i].floatValue += dt * (0.45f + 0.2f * i) * boost * (1f + 0.3f * e)
                }
            }
        }
    }
    return clock
}

fun energyForMbps(mbps: Double): Float = (log10(1.0 + mbps) / 3.0).toFloat().coerceIn(0f, 1f)
