package dev.dougie.comet.ui

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
fun moonColors(dark: Boolean) =
    if (dark) listOf(PingColor, DownloadColor, UploadColor)
    else listOf(Color(0xFFB86E00), Color(0xFF007FA8), Color(0xFF9B2FD0))

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

/** Corona glow extends to this multiple of the planet radius. */
private const val CoronaScale = 1.35f
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
    val dark: Boolean,
    /** Text shown on each moon (null = none yet), and a smaller line shown under it once landed. */
    val values: List<String?>,
    val subs: List<String?>,
    /** Animated radius of each moon, in planet radii. Read only while drawing. */
    val moonScale: List<State<Float>>,
    /** 0 = moons in orbit, 1 = all three landed at the bottom as the results. */
    val fly: State<Float>,
)

private val MoonLabels = listOf("PING", "DOWNLOAD", "UPLOAD")
private val MoonUnits = listOf("ms", "Mbps", "Mbps")
const val MoonScaleIdle = 0.13f
const val MoonScaleDone = 0.24f
const val MoonScaleActive = 0.34f

private const val SlotY = 0.8f
private const val SlotRadiusFraction = 0.095f

/** Moons leave one after another; returns 0..1 eased progress of moon [i]'s flight. */
private fun landed(input: SceneInput, i: Int): Float {
    val t = ((input.fly.value - i * 0.12f) / 0.76f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Cel-shading ink: every shape gets a dark outline and flat, hard-edged tones. */
private val Ink = Color(0xFF070A1C)

fun DrawScope.drawSpace(clock: SceneClock, input: SceneInput, text: TextMeasurer, planetRadius: Float, planetCenter: Offset) {
    val dark = input.dark
    // Stepped sky: flat horizontal bands instead of a smooth gradient.
    val ends = if (dark) Space to SpaceDeep else Color(0xFFB4D0FF) to Color(0xFFE0EBFF)
    val bands = List(9) { i ->
        val t = 1f - kotlin.math.abs(i - 4) / 4f // 0 at the edges, 1 in the middle
        lerp(ends.first, ends.second, t)
    }
    val bandHeight = size.height / bands.size
    bands.forEachIndexed { i, c -> drawRect(c, Offset(0f, i * bandHeight), androidx.compose.ui.geometry.Size(size.width, bandHeight + 1f)) }
    drawStars(clock.starScroll.floatValue, input.energy, if (dark) Color.White else Color(0xFF2A3A7A))

    // The comet is far behind the planet: draw it in a layer, then erase everything the planet and
    // its (flat, hard-edged) corona cover.
    val coronaRadius = planetRadius * CoronaScale
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        drawComet(clock.cometProgress.floatValue, clock.comet.value, dark)
        drawCircle(Color.Black, coronaRadius, planetCenter, blendMode = BlendMode.DstOut)
        canvas.restore()
    }

    val colors = moonColors(dark)
    val accent = if (input.activeMoon >= 0) colors[input.activeMoon] else colors[1]
    for (i in 0..2) drawOrbit(i, planetCenter, planetRadius, dark)
    for (i in 0..2) if (!moonInFront(clock, input, i)) drawMoon(i, clock, input, text, planetCenter, planetRadius, colors)
    drawPlanet(clock.planetSpin.floatValue, accent, planetCenter, planetRadius)
    for (i in 0..2) if (moonInFront(clock, input, i)) drawMoon(i, clock, input, text, planetCenter, planetRadius, colors)
}

private fun moonInFront(clock: SceneClock, input: SceneInput, i: Int) = landed(input, i) > 0f || sin(clock.orbit[i].floatValue) > 0f

private fun DrawScope.drawStars(scroll: Float, energy: Float, tint: Color) {
    val streak = size.width * 0.05f * energy
    for (s in stars) {
        val x = ((s.x - scroll * s.depth) % 1f + 1f) % 1f * size.width
        val y = s.y * size.height
        val color = tint.copy(alpha = if (s.depth > 0.6f) 1f else 0.6f)
        if (streak > 2f) {
            drawLine(color, Offset(x, y), Offset(x + streak * s.depth, y), strokeWidth = 2f + 2f * s.depth, cap = StrokeCap.Butt)
        } else if (s.depth > 0.8f) {
            // Four-point sparkle on the nearest stars.
            val k = 5f + 5f * s.depth
            val spark = Path().apply {
                moveTo(x, y - k); lineTo(x + k * 0.3f, y - k * 0.3f); lineTo(x + k, y); lineTo(x + k * 0.3f, y + k * 0.3f)
                lineTo(x, y + k); lineTo(x - k * 0.3f, y + k * 0.3f); lineTo(x - k, y); lineTo(x - k * 0.3f, y - k * 0.3f); close()
            }
            drawPath(spark, color)
        } else {
            drawCircle(color, radius = 1.5f + 2.5f * s.depth, center = Offset(x, y))
        }
    }
}

private fun DrawScope.drawComet(t: Float, pass: CometPass, dark: Boolean) {
    val from = Offset(pass.fromX * size.width, pass.fromY * size.height)
    val to = Offset(pass.toX * size.width, pass.toY * size.height)
    val head = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    val len = kotlin.math.hypot(to.x - from.x, to.y - from.y)
    val dir = Offset((to.x - from.x) / len, (to.y - from.y) / len)
    val normal = Offset(-dir.y, dir.x)
    val tailLength = size.width * 0.45f * pass.scale
    val w = size.width * 0.009f * pass.scale
    val core = if (dark) Color(0xFFE8F8FF) else Color(0xFFFFFFFF)
    val glow = if (dark) Color(0xFF4DD8FF) else Color(0xFF2A8FD0)

    // Flat, pointed tail in three nested tones, each with a hard edge.
    fun tail(width: Float, length: Float, color: Color) {
        val tip = Offset(head.x - dir.x * length, head.y - dir.y * length)
        drawPath(
            Path().apply {
                moveTo(head.x + normal.x * width, head.y + normal.y * width)
                lineTo(tip.x, tip.y)
                lineTo(head.x - normal.x * width, head.y - normal.y * width)
                close()
            },
            color,
        )
    }
    tail(w * 3.2f, tailLength, glow.copy(alpha = 0.35f))
    tail(w * 1.8f, tailLength * 0.75f, glow)
    tail(w * 0.8f, tailLength * 0.5f, core)
    drawCircle(Ink, w * 3.4f, head)
    drawCircle(core, w * 2.6f, head)
}

private fun DrawScope.drawOrbit(i: Int, c: Offset, r: Float, dark: Boolean) {
    val rx = r * OrbitRadius[i]
    rotate(OrbitTilt, c) {
        drawOval(
            (if (dark) Color.White else Ink).copy(alpha = if (dark) 0.35f else 0.45f),
            topLeft = Offset(c.x - rx, c.y - rx * 0.28f),
            size = androidx.compose.ui.geometry.Size(rx * 2, rx * 0.56f),
            style = Stroke(3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
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

private fun DrawScope.drawMoon(
    i: Int, clock: SceneClock, input: SceneInput, text: TextMeasurer, c: Offset, r: Float, colors: List<Color>,
) {
    val f = landed(input, i)
    val slot = Offset(size.width * (1f / 6f + i / 3f), size.height * SlotY)
    val slotRadius = size.width * SlotRadiusFraction
    val orbitRadius = r * input.moonScale[i].value
    val pos = moonPosition(clock, i, c, r).let { Offset(it.x + (slot.x - it.x) * f, it.y + (slot.y - it.y) * f) }
    val radius = orbitRadius + (slotRadius - orbitRadius) * f

    val active = input.activeMoon == i
    val lit = active || input.moonDone[i]
    val base = colors[i]
    val body = if (lit) base else lerp(base, Color(0xFF3A4060), 0.75f)
    val outline = radius * 0.09f

    // Hard-edged halo ring instead of a soft glow.
    if (lit) drawCircle(base.copy(alpha = if (active) 0.3f else 0.18f), radius * (if (active) 1.55f else 1.4f), pos)
    drawCircle(Ink, radius + outline, pos)
    drawCircle(body, radius, pos)
    // Two flat shadow tones on the lower right, then a hard highlight.
    val moon = Path().apply { addOval(Rect(pos, radius)) }
    clipPath(moon) {
        val light = Path().apply { addOval(Rect(pos + Offset(-radius * 0.22f, -radius * 0.22f), radius)) }
        clipPath(light, ClipOp.Difference) { drawCircle(Color.Black.copy(alpha = 0.28f), radius, pos) }
        val lighter = Path().apply { addOval(Rect(pos + Offset(-radius * 0.5f, -radius * 0.5f), radius)) }
        clipPath(lighter, ClipOp.Difference) { drawCircle(Color.Black.copy(alpha = 0.22f), radius, pos) }
    }
    drawCircle(Color.White.copy(alpha = 0.75f), radius * 0.13f, pos + Offset(-radius * 0.45f, -radius * 0.5f))

    if (active) {
        drawArc(
            Color.White, -90f, 360f * input.moonProgress, false,
            topLeft = pos - Offset(radius * 1.25f, radius * 1.25f),
            size = androidx.compose.ui.geometry.Size(radius * 2.5f, radius * 2.5f),
            style = Stroke(6f, cap = StrokeCap.Butt),
        )
    }

    input.values[i]?.let { value ->
        drawCentered(text, value, pos + Offset(0f, -radius * 0.12f), radius * 0.62f, Color.White, FontWeight.Bold)
        drawCentered(text, MoonUnits[i], pos + Offset(0f, radius * 0.45f), radius * 0.26f, Color.White, FontWeight.Bold)
    }
    if (f > 0f) {
        val label = base.copy(alpha = f)
        drawCentered(text, MoonLabels[i], pos + Offset(0f, radius + size.width * 0.045f), size.width * 0.03f, label, FontWeight.Bold, 1.5.sp, f)
        input.subs[i]?.let {
            drawCentered(text, it, pos + Offset(0f, radius + size.width * 0.095f), size.width * 0.028f, label, FontWeight.Bold, alpha = f)
        }
    }
}

/** Text with a bold ink outline, drawn as a stroked pass under the fill. */
private fun DrawScope.drawCentered(
    measurer: TextMeasurer, string: String, center: Offset, sizePx: Float, color: Color,
    weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = TextUnit.Unspecified, alpha: Float = 1f,
) {
    fun style(c: Color, draw: androidx.compose.ui.graphics.drawscope.DrawStyle? = null) = TextStyle(
        fontSize = sizePx.toSp(), color = c, fontWeight = weight, letterSpacing = letterSpacing,
        drawStyle = draw,
    )
    val fill = measurer.measure(string, style(color))
    val topLeft = center - Offset(fill.size.width / 2f, fill.size.height / 2f)
    val outline = measurer.measure(string, style(Ink.copy(alpha = alpha), Stroke(width = sizePx * 0.13f, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
    drawText(outline, topLeft = topLeft)
    drawText(fill, topLeft = topLeft)
}

private fun DrawScope.drawPlanet(spin: Float, accent: Color, c: Offset, r: Float) {
    // Corona: two flat, hard-edged rings.
    drawCircle(accent.copy(alpha = 0.16f), r * CoronaScale, c)
    drawCircle(accent.copy(alpha = 0.3f), r * 1.17f, c)
    drawCircle(Ink, r * 1.04f, c)

    val ocean = lerp(Color(0xFF2F6BC0), accent, 0.3f)
    val disc = Path().apply { addOval(Rect(c, r)) }
    clipPath(disc) {
        drawCircle(ocean, r, c)
        // Latitude bands: alternating flat stripes.
        for (k in -4..4) {
            if (k and 1 == 0) continue
            drawRect(lerp(ocean, accent, 0.45f), Offset(c.x - r, c.y + k * r * 0.22f - r * 0.07f), androidx.compose.ui.geometry.Size(2 * r, r * 0.14f))
        }
        // Surface features sliding around the sphere, flat with an ink edge.
        for (b in blobs) {
            val lon = b.lon + spin
            val depth = cos(lon)
            if (depth <= 0.1f) continue
            val x = c.x + r * cos(b.lat) * sin(lon)
            val y = c.y + r * sin(b.lat)
            val w = r * b.size * 2f * (0.35f + 0.65f * depth)
            val h = r * b.size * 1.2f
            val topLeft = Offset(x - w, y - h)
            val sz = androidx.compose.ui.geometry.Size(w * 2, h * 2)
            drawOval(lerp(accent, Color.White, 0.55f + 0.25f * b.tone), topLeft, sz)
            drawOval(Ink.copy(alpha = 0.55f), topLeft, sz, style = Stroke(r * 0.02f))
        }
        // Two hard shadow tones toward the lower right (light from the upper left).
        val lit1 = Path().apply { addOval(Rect(c + Offset(-r * 0.2f, -r * 0.2f), r)) }
        clipPath(lit1, ClipOp.Difference) { drawCircle(Color.Black.copy(alpha = 0.3f), r, c) }
        val lit2 = Path().apply { addOval(Rect(c + Offset(-r * 0.5f, -r * 0.5f), r)) }
        clipPath(lit2, ClipOp.Difference) { drawCircle(Color.Black.copy(alpha = 0.25f), r, c) }
        // Specular highlight.
        drawOval(Color.White.copy(alpha = 0.55f), c + Offset(-r * 0.62f, -r * 0.62f), androidx.compose.ui.geometry.Size(r * 0.3f, r * 0.17f))
    }
    drawCircle(Ink, r, c, style = Stroke(r * 0.045f))
}
