package cc.samlab.ticket.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveEmptyState(
    message: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
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
    // Read the phase only in Canvas: the page and its text do not recompose each frame.
    val phase = if (visible && resumed) {
        rememberInfiniteTransition(label = "expressive-empty").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
            label = "expressive-empty-morph",
        )
    } else remember { mutableFloatStateOf(0f) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(artworkModifier.size(160.dp)) {
            val amount = phase.value
            scale(size.width, size.height, pivot = Offset.Zero) {
                rotate(amount * 24f, pivot = Offset(0.5f, 0.5f)) {
                    drawPath(morph.toPath(amount).asComposePath(), colors.secondaryContainer)
                }
                repeat(3) { index ->
                    val wave = sin(amount * PI.toFloat() + index * 0.8f)
                    val height = 0.2f + index * 0.1f + wave * 0.05f
                    drawRoundRect(
                        color = colors.primary,
                        topLeft = Offset(0.28f + index * 0.17f, 0.72f - height),
                        size = Size(0.1f, height),
                        cornerRadius = CornerRadius(0.035f),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
