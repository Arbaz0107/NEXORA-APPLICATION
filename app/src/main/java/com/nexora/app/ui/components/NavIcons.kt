package com.nexora.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Links tab icon: a chain link drawn as two open capsule halves with a short bar between them,
 * set on the diagonal. Stroke only, so the tab bar can tint it for the selected and idle states.
 */
val NexoraLinkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "NexoraLink",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        group(rotate = -45f, pivotX = 12f, pivotY = 12f, scaleX = 0.85f, scaleY = 0.85f) {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.35f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // left half, open on the right
                moveTo(9f, 17f)
                horizontalLineTo(7f)
                arcTo(5f, 5f, 0f, false, true, 7f, 7f)
                horizontalLineTo(9f)
                // right half, open on the left
                moveTo(15f, 7f)
                horizontalLineTo(17f)
                arcTo(5f, 5f, 0f, true, true, 17f, 17f)
                horizontalLineTo(15f)
                // the bar between them
                moveTo(8f, 12f)
                horizontalLineTo(16f)
            }
        }
    }.build()
}
