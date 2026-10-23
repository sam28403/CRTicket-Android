package cc.samlab.ticket.ui.sale

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import cc.samlab.ticket.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SaleDayCompleteState(active: Boolean, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val phase = if (active && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        rememberInfiniteTransition(label = "sale-day-complete").animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "complete-badge-morph",
        )
    } else remember { mutableFloatStateOf(0f) }
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.testTag("sale-day-complete").padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val amount = phase.value
                scale(size.width, size.height, pivot = Offset.Zero) {
                    rotate(amount * 16f - 8f, Offset(0.5f, 0.5f)) {
                        drawPath(morph.toPath(amount).asComposePath(), colors.tertiaryContainer)
                    }
                }
            }
            Icon(Icons.Rounded.Check, null, Modifier.size(72.dp), tint = colors.onTertiaryContainer)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.sale_day_complete),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
