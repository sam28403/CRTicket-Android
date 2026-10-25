package cc.samlab.ticket.ui.welcome

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
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
internal fun WelcomeBackupArtwork() {
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
    val phase = if (resumed) {
        val transition = rememberInfiniteTransition(label = "backup-restore")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
            label = "ticket-transfer",
        )
        value
    } else 0f
    val wave = sin(phase * 2f * PI).toFloat()
    val transfer = (1f - kotlin.math.cos(phase * 2f * PI).toFloat()) / 2f
    val colors = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    Box(Modifier.size(240.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val extent = size.minDimension * 0.84f
            translate((size.width - extent) / 2f, (size.height - extent) / 2f) {
                scale(extent, pivot = Offset.Zero) {
                    rotate(wave * 12f, Offset(0.5f, 0.5f)) {
                        drawPath(
                            morph.toPath(0.35f + wave * 0.25f).asComposePath(),
                            colors.primaryContainer,
                        )
                    }
                }
            }
            drawCircle(colors.tertiaryContainer, 12.dp.toPx(),
                Offset(size.width * 0.86f, size.height * 0.24f + wave * 6.dp.toPx()))
            drawCircle(colors.secondaryContainer, 8.dp.toPx(),
                Offset(size.width * 0.12f, size.height * 0.74f - wave * 4.dp.toPx()))
        }
        Icon(
            Icons.Rounded.Archive, null,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 42.dp).size(76.dp),
            tint = colors.onPrimaryContainer,
        )
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
                .graphicsLayer {
                    translationY = transfer * 34.dp.toPx()
                    rotationZ = -10f + transfer * 10f
                    scaleX = 1f - transfer * 0.12f
                    scaleY = scaleX
                }
                .background(colors.tertiaryContainer, RoundedCornerShape(20.dp))
                .padding(12.dp),
        ) {
            Icon(Icons.Rounded.ConfirmationNumber, null,
                Modifier.size(42.dp), tint = colors.onTertiaryContainer)
        }
        Icon(
            Icons.Rounded.ArrowDownward, null,
            Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
                .offset(y = (wave * 6).dp).size(28.dp),
            tint = colors.tertiary,
        )
    }
}

