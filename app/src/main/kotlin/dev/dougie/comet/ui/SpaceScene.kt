package dev.dougie.comet.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import dev.dougie.comet.ui.theme.DownloadColor
import dev.dougie.comet.ui.theme.PingColor
import dev.dougie.comet.ui.theme.Space
import dev.dougie.comet.ui.theme.SpaceDeep
import dev.dougie.comet.ui.theme.UploadColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Moon index: 0 = ping, 1 = download, 2 = upload. */
val MoonColors = listOf(PingColor, DownloadColor, UploadColor)

private class Star(val x: Float, val y: Float, val depth: Float)

private val stars = Random(7).let { r -> List(140) { Star(r.nextFloat(), r.nextFloat(), 0.2f + 0.8f * r.nextFloat()) } }

private class Blob(val lon: Float, val lat: Float, val size: Float, val tone: Float)

private val blobs = Random(21).let { r ->
    List(14) {
        Blob(
            lon = r.nextFloat() * 2 * PI.toFloat(),
            lat = (r.nextFloat() - 0.5f) * 1.6f,
            size = 0.12f + 0.16f * r.nextFloat(),
            tone = r.nextFloat(),
        )
    }
}

private const val OrbitTilt = -16f
private val OrbitRadius = floatArrayOf(1.5f, 1.85f, 2.2f)

/** What the scene needs to know, kept free of Compose state so drawing stays cheap. */
class SceneInput(
    val energy: Float,
    /** Which moon is being measured, or -1. */
    val activeMoon: Int,
    /** Which moons already have a result. */
    val moonDone: BooleanArray,
    val moonProgress: Float,
)

fun DrawScope.drawSpace(clock: SceneClock, input: SceneInput, planetRadius: Float, planetCenter: Offset) {
    drawRect(Brush.verticalGradient(listOf(Space, SpaceDeep, Space)))
    drawStars(clock.starScroll.floatValue, input.energy)
    drawComet(clock.cometProgress.floatValue)

    val accent = if (input.activeMoon >= 0) MoonColors[input.activeMoon] else DownloadColor
    for (i in 0..2) drawOrbit(i, planetCenter, planetRadius)
    for (i in 0..2) if (!moonInFront(clock, i)) drawMoon(i, clock, input, planetCenter, planetRadius)
    drawPlanet(clock.planetSpin.floatValue, accent, planetCenter, planetRadius)
    for (i in 0..2) if (moonInFront(clock, i)) drawMoon(i, clock, input, planetCenter, planetRadius)
}

private fun moonInFront(clock: SceneClock, i: Int) = sin(clock.orbit[i].floatValue) > 0f

private fun DrawScope.drawStars(scroll: Float, energy: Float) {
    val streak = size.width * 0.05f * energy
    for (s in stars) {
        val x = ((s.x - scroll * s.depth) % 1f + 1f) % 1f * size.width
        val y = s.y * size.height
        val color = Color.White.copy(alpha = 0.25f + 0.6f * s.depth)
        if (streak > 2f) {
            drawLine(color, Offset(x, y), Offset(x + streak * s.depth, y), strokeWidth = 1f + 1.5f * s.depth, cap = StrokeCap.Round)
        } else {
            drawCircle(color, radius = 0.8f + 1.6f * s.depth, center = Offset(x, y))
        }
    }
}

private fun DrawScope.drawComet(t: Float) {
    // Flies bottom-left to top-right, tail trailing behind.
    val from = Offset(-0.25f * size.width, 0.92f * size.height)
    val to = Offset(1.25f * size.width, 0.06f * size.height)
    val head = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    val dir = Offset(to.x - from.x, to.y - from.y).let { val l = kotlin.math.hypot(it.x, it.y); Offset(it.x / l, it.y / l) }
    val normal = Offset(-dir.y, dir.x)
    val tailLength = size.width * 0.7f
    val tail = Offset(head.x - dir.x * tailLength, head.y - dir.y * tailLength)
    val w = size.width * 0.012f

    val path = Path().apply {
        moveTo(head.x + normal.x * w, head.y + normal.y * w)
        lineTo(tail.x, tail.y)
        lineTo(head.x - normal.x * w, head.y - normal.y * w)
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(Color(0xFFE8F8FF), Color(0x884DD8FF), Color.Transparent), head, tail))
    // Wider, fainter dust tail.
    val dust = Path().apply {
        moveTo(head.x + normal.x * w * 3, head.y + normal.y * w * 3)
        lineTo(tail.x, tail.y)
        lineTo(head.x - normal.x * w * 3, head.y - normal.y * w * 3)
        close()
    }
    drawPath(dust, Brush.linearGradient(listOf(Color(0x334DD8FF), Color.Transparent), head, tail))
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0x884DD8FF), Color.Transparent), head, w * 5), w * 5, head)
}

private fun DrawScope.drawOrbit(i: Int, c: Offset, r: Float) {
    val rx = r * OrbitRadius[i]
    rotate(OrbitTilt, c) {
        drawOval(
            Color.White.copy(alpha = 0.12f),
            topLeft = Offset(c.x - rx, c.y - rx * 0.28f),
            size = androidx.compose.ui.geometry.Size(rx * 2, rx * 0.56f),
            style = Stroke(1.5f),
        )
    }
}

private fun moonPosition(clock: SceneClock, i: Int, c: Offset, r: Float): Offset {
    val a = clock.orbit[i].floatValue
    val rx = r * OrbitRadius[i]
    val lx = rx * cos(a)
    val ly = rx * 0.28f * sin(a)
    val t = OrbitTilt * PI.toFloat() / 180f
    return Offset(c.x + lx * cos(t) - ly * sin(t), c.y + lx * sin(t) + ly * cos(t))
}

private fun DrawScope.drawMoon(i: Int, clock: SceneClock, input: SceneInput, c: Offset, r: Float) {
    val pos = moonPosition(clock, i, c, r)
    val active = input.activeMoon == i
    val lit = active || input.moonDone[i]
    val base = MoonColors[i]
    val radius = r * (if (active) 0.17f else 0.13f)
    val body = if (lit) base else lerp(base, Color(0xFF3A4060), 0.75f)

    if (lit) drawCircle(Brush.radialGradient(listOf(base.copy(alpha = if (active) 0.55f else 0.3f), Color.Transparent), pos, radius * 3.2f), radius * 3.2f, pos)
    drawCircle(body, radius, pos)
    // Terminator shading
    drawCircle(
        Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), pos + Offset(-radius * 0.4f, -radius * 0.4f), radius * 1.5f),
        radius, pos,
    )
    if (active) {
        drawArc(
            Color.White, -90f, 360f * input.moonProgress, false,
            topLeft = pos - Offset(radius * 1.5f, radius * 1.5f),
            size = androidx.compose.ui.geometry.Size(radius * 3f, radius * 3f),
            style = Stroke(3f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawPlanet(spin: Float, accent: Color, c: Offset, r: Float) {
    val ocean = lerp(Color(0xFF1B3A6B), accent, 0.25f)
    // Atmosphere glow
    drawCircle(Brush.radialGradient(0.78f to Color.Transparent, 0.86f to accent.copy(alpha = 0.35f), 1f to Color.Transparent, center = c, radius = r * 1.35f), r * 1.35f, c)

    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    clipPath(disc) {
        drawCircle(Brush.verticalGradient(listOf(lerp(ocean, Color.White, 0.15f), ocean, lerp(ocean, Color.Black, 0.4f)), c.y - r, c.y + r), r, c)
        // Latitude bands
        for (k in -4..4) {
            val y = c.y + k * r * 0.22f
            drawRect(accent.copy(alpha = 0.10f + 0.04f * (k and 1)), Offset(c.x - r, y), androidx.compose.ui.geometry.Size(2 * r, r * 0.09f))
        }
        // Surface features sliding around the sphere
        for (b in blobs) {
            val lon = b.lon + spin
            val depth = cos(lon)
            if (depth <= 0f) continue
            val x = c.x + r * cos(b.lat) * sin(lon)
            val y = c.y + r * sin(b.lat)
            val w = r * b.size * 2f * (0.35f + 0.65f * depth)
            val h = r * b.size * 1.2f
            drawOval(lerp(accent, Color.White, 0.3f + 0.4f * b.tone).copy(alpha = 0.45f * depth), Offset(x - w, y - h), androidx.compose.ui.geometry.Size(w * 2, h * 2))
        }
        // Light from the upper left
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)), c + Offset(-r * 0.35f, -r * 0.35f), r * 1.5f),
            r, c,
        )
    }
    drawCircle(accent.copy(alpha = 0.5f), r, c, style = Stroke(2f))
}
