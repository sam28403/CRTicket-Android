package cc.samlab.ticket.ui.settings

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.sin

internal fun DrawScope.arrivalBadge(
    morph: Morph,
    progress: Float,
    origin: Offset,
    extent: Float,
    color: Color,
    angle: Float,
) {
    translate(origin.x, origin.y) {
        rotate(angle, Offset(extent / 2, extent / 2)) {
            scale(extent, extent, pivot = Offset.Zero) {
                drawPath(morph.toPath(progress).asComposePath(), color)
            }
        }
    }
}

internal fun DrawScope.arrivalTrain(center: Offset, color: Color, windowColor: Color) {
    translate(center.x, center.y) {
        drawRoundRect(color, Offset(-22f, -27f), Size(44f, 46f), CornerRadius(11f))
        drawRoundRect(windowColor, Offset(-15f, -20f), Size(30f, 18f), CornerRadius(4f))
        drawCircle(windowColor, 3f, Offset(-12f, 10f))
        drawCircle(windowColor, 3f, Offset(12f, 10f))
        drawLine(color, Offset(-12f, 19f), Offset(-20f, 29f), 4f, StrokeCap.Round)
        drawLine(color, Offset(12f, 19f), Offset(20f, 29f), 4f, StrokeCap.Round)
        drawLine(color, Offset(-16f, 26f), Offset(16f, 26f), 3f, StrokeCap.Round)
    }
}

internal fun DrawScope.arrivalStation(center: Offset, color: Color) {
    translate(center.x, center.y) {
        val roof = Path().apply {
            moveTo(-28f, -10f)
            lineTo(0f, -29f)
            lineTo(28f, -10f)
        }
        drawPath(roof, color, style = Stroke(4f, cap = StrokeCap.Round))
        drawRoundRect(color, Offset(-22f, -10f), Size(44f, 34f), CornerRadius(3f),
            style = Stroke(4f))
        drawLine(color, Offset(-29f, 26f), Offset(29f, 26f), 4f, StrokeCap.Round)
        drawLine(color, Offset(-7f, 23f), Offset(-7f, 8f), 4f, StrokeCap.Round)
        drawLine(color, Offset(7f, 23f), Offset(7f, 8f), 4f, StrokeCap.Round)
        drawLine(color, Offset(-7f, 8f), Offset(7f, 8f), 4f, StrokeCap.Round)
        drawCircle(color, 4f, Offset(0f, -10f))
    }
}

internal fun DrawScope.arrivalSignal(phase: Float, color: Color) {
    repeat(4) { index ->
        val pulse = (sin(phase * 2f * PI - index * 0.8f).toFloat() + 1f) / 2f
        val height = 10f + pulse * 29f
        val x = 142f + index * 12f
        drawLine(
            color.copy(alpha = 0.45f + pulse * 0.55f),
            Offset(x, 118f - height / 2f),
            Offset(x, 118f + height / 2f),
            5f,
            StrokeCap.Round,
        )
    }
}
