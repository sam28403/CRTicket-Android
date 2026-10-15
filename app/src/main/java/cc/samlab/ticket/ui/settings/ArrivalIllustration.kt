package cc.samlab.ticket.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.sin

/** Original vector artwork: a train approaches a station while a location signal pulses. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArrivalIllustration(visible: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val phase = if (visible && resumed) {
        val transition = rememberInfiniteTransition(label = "arrival-illustration")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
            label = "arrival-signal",
        )
        value
    } else 0f
    val colors = MaterialTheme.colorScheme
    val trainMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val stationMorph = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Circle) }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = colors.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.38f).clearAndSetSemantics {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = minOf(size.width / 320f, size.height / 232f)
            translate((size.width - 320f * unit) / 2f, (size.height - 232f * unit) / 2f) {
                scale(unit, unit, pivot = Offset.Zero) {
                    val wave = sin(phase * 2f * PI).toFloat()
                    arrivalBadge(trainMorph, 0.18f + wave * 0.15f,
                        Offset(25f + wave * 4f, 66f), 104f, colors.primary, wave * 5f)
                    arrivalBadge(stationMorph, 0.18f - wave * 0.15f,
                        Offset(197f, 66f), 104f, colors.tertiary, -wave * 4f)
                    arrivalTrain(Offset(77f + wave * 4f, 118f), colors.onPrimary, colors.primary)
                    arrivalStation(Offset(249f, 118f), colors.onTertiary)
                    arrivalSignal(phase, colors.secondary)
                    drawCircle(colors.secondaryContainer, 10f, Offset(121f, 39f + wave * 4f))
                    drawCircle(colors.tertiaryContainer, 7f, Offset(190f, 49f - wave * 5f))
                    drawCircle(colors.primaryContainer, 11f, Offset(146f, 191f - wave * 4f))
                    drawCircle(colors.secondaryContainer, 7f, Offset(189f, 185f + wave * 3f))
                }
            }
        }
    }
}
