package cc.samlab.ticket.ui.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun SearchEmptyState(title: Int, hint: Int, active: Boolean, compact: Boolean) {
    val layout = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)
        .testTag("search-empty-state")
    if (compact) {
        Row(layout, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SearchIllustration(active, Modifier.size(72.dp))
            SearchEmptyText(title, hint, Modifier.weight(1f), centered = false)
        }
    } else {
        Column(layout, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SearchIllustration(active, Modifier.size(128.dp))
            SearchEmptyText(title, hint)
        }
    }
}

@Composable
private fun SearchEmptyText(title: Int, hint: Int, modifier: Modifier = Modifier, centered: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        val align = if (centered) TextAlign.Center else TextAlign.Start
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = align)
        Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium,
            textAlign = align, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchIllustration(active: Boolean, modifier: Modifier) {
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
    val phase = if (active && resumed) {
        rememberInfiniteTransition(label = "search-idle").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "search-morph",
        )
    } else remember { mutableFloatStateOf(0f) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.clearAndSetSemantics {}) {
        val amount = phase.value
        val wave = sin(amount * PI.toFloat() * 2f)
        scale(size.width, size.height, pivot = Offset.Zero) {
            rotate(amount * 35f, pivot = Offset(0.5f, 0.5f)) {
                drawPath(morph.toPath(amount).asComposePath(), colors.secondaryContainer)
            }
            val center = Offset(0.44f + wave * 0.04f, 0.43f)
            drawCircle(colors.surfaceContainerLowest, 0.20f, center)
            drawCircle(colors.primary, 0.20f, center, style = Stroke(0.045f))
            drawLine(colors.primary, center + Offset(0.15f, 0.15f),
                center + Offset(0.32f, 0.32f), 0.065f, StrokeCap.Round)
            drawCircle(colors.tertiary, 0.035f, Offset(0.77f, 0.25f + wave * 0.04f))
            drawCircle(colors.primary.copy(alpha = 0.45f), 0.022f,
                Offset(0.21f, 0.72f - wave * 0.04f))
        }
    }
}
