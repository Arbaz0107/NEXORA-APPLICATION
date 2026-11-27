package com.nexora.app.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexora.app.ui.theme.Neon

/**
 * The app's name as a wordmark: heavy, wide-tracked capitals in a font of its own (the system "black" face), so it
 * stands apart from every other piece of text in the app. The X carries the accent colour.
 */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, size: Float = 30f) {
    val face = remember { FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL)) }
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accent = if (light) Neon.coral else Neon.lime
    val text = MaterialTheme.colorScheme.onBackground
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = text)) { append("NE") }
            withStyle(SpanStyle(color = accent)) { append("X") }
            withStyle(SpanStyle(color = text)) { append("ORA") }
        },
        modifier = modifier,
        fontFamily = face,
        fontWeight = FontWeight.Black,
        fontStyle = FontStyle.Italic,
        fontSize = size.sp,
        letterSpacing = 3.sp,
        maxLines = 1
    )
}

/** A complete video glyph: a rounded screen with a solid play triangle, drawn to match the other quick-action icons. */
@Composable
fun VideoGlyph(tint: Color, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(tint, Offset(2f * u, 4.5f * u), Size(20f * u, 15f * u), CornerRadius(4.5f * u), style = stroke)
        val play = Path().apply {
            moveTo(10f * u, 8.6f * u)
            lineTo(15.6f * u, 12f * u)
            lineTo(10f * u, 15.4f * u)
            close()
        }
        drawPath(play, tint)
        drawPath(play, tint, style = Stroke(width = 1.4f * u, join = StrokeJoin.Round))
    }
}
