package cc.samlab.ticket.ui.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SaleReminderIllustration(visible: Boolean) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val phase = if (visible && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        rememberInfiniteTransition(label = "sale-clock").animateFloat(
            0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse),
            label = "clock-pulse",
        )
    } else remember { mutableFloatStateOf(0f) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxWidth().height(210.dp).clearAndSetSemantics {}) {
        val unit = minOf(size.width, size.height) * 0.8f
        drawContext.transform.translate((size.width - unit) / 2, (size.height - unit) / 2)
        scale(unit, unit, pivot = Offset.Zero) {
            val pulse = phase.value
            rotate(pulse * 12 - 6, Offset(0.5f, 0.5f)) {
                drawPath(morph.toPath(pulse).asComposePath(), colors.tertiaryContainer)
            }
            drawCircle(colors.surface, 0.31f, Offset(0.5f, 0.5f))
            repeat(12) {
                val angle = it * PI / 6
                drawCircle(colors.outline, 0.012f,
                    Offset(0.5f + cos(angle).toFloat() * 0.25f,
                        0.5f + sin(angle).toFloat() * 0.25f))
            }
            drawLine(colors.primary, Offset(0.5f, 0.5f), Offset(0.5f, 0.33f),
                0.035f, androidx.compose.ui.graphics.StrokeCap.Round)
            rotate(25 + pulse * 35, Offset(0.5f, 0.5f)) {
                drawLine(colors.primary, Offset(0.5f, 0.5f), Offset(0.68f, 0.5f),
                    0.035f, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(colors.primary, 0.03f, Offset(0.5f, 0.5f))
        }
    }
}
