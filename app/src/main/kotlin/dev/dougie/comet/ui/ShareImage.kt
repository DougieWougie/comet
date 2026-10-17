package dev.dougie.comet.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CardWidth = 1080
private const val CardHeight = 1350 // 4:5, which chat and social apps crop least
private const val SharedDir = "shared"
private val Ink = Color(0xFF070A1C)

/**
 * Draws the results scene (planet above, graded moons landed below) with the same code as the live
 * screen, frozen in a fixed pose and with a title above it.
 */
fun renderShareCard(context: Context, card: ShareCard, dark: Boolean): Bitmap {
    // Density 1 so sp == px; the measurer must agree with the draw scope or text is scaled twice.
    val density = Density(1f)
    val text = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)

    val clock = SceneClock().apply {
        planetSpin.floatValue = 0.6f
        cometProgress.floatValue = 0.5f
    }
    val input = SceneInput(
        energy = 0f,
        activeMoon = -1,
        moonDone = booleanArrayOf(true, true, true),
        moonProgress = 0f,
        dark = dark,
        values = card.values,
        subs = card.subs,
        moonScale = List(3) { mutableFloatStateOf(MoonScaleDone) },
        fly = mutableFloatStateOf(1f),
        ship = mutableFloatStateOf(if (card.vpn) 1f else 0f),
        grades = card.grades,
        shipDown = mutableFloatStateOf(1f),
    )

    val image = ImageBitmap(CardWidth, CardHeight)
    val size = Size(CardWidth.toFloat(), CardHeight.toFloat())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
        val radius = minOf(size.width, size.height) * 0.19f
        drawSpace(clock, input, text, radius, Offset(size.width / 2, size.height * 0.4f))

        val fg = if (dark) Color.White else Ink
        drawTitle(text, "COMET", Offset(size.width / 2, size.height * 0.07f), 64f, fg, FontWeight.ExtraBold, 8.sp)
        drawTitle(text, card.subtitle, Offset(size.width / 2, size.height * 0.115f), 34f, fg.copy(alpha = 0.8f), FontWeight.Medium)
    }
    return image.asAndroidBitmap()
}

private fun DrawScope.drawTitle(
    text: TextMeasurer, string: String, center: Offset, px: Float, color: Color, weight: FontWeight,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    val layout = text.measure(
        string,
        TextStyle(fontSize = px.sp, color = color, fontWeight = weight, letterSpacing = letterSpacing),
    )
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

/** Renders [card] to a PNG in the cache and opens the system share sheet for it. */
suspend fun shareResult(context: Context, card: ShareCard, dark: Boolean) {
    val file = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, SharedDir).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "comet-${System.currentTimeMillis()}.png")
        val bitmap = renderShareCard(context, card, dark)
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, card.summary)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
