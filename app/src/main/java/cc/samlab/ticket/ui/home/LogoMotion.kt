package cc.samlab.ticket.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import cc.samlab.ticket.ui.MaterialMorphShape
import kotlinx.coroutines.launch

internal class LogoMotion(
    val shape: Shape,
    val modifier: Modifier,
    val play: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberLogoMotion(interactions: MutableInteractionSource): LogoMotion {
    val pressed by interactions.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "logo-press",
    )
    val pulse = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Circle) }
    val amount = maxOf(press, pulse.value).coerceIn(0f, 1f)
    return LogoMotion(
        shape = MaterialMorphShape(morph, amount),
        modifier = Modifier.graphicsLayer {
            scaleX = 1f - amount * 0.1f
            scaleY = 1f - amount * 0.1f
            rotationZ = amount * 12f
        },
        play = {
            // Guarantee a visible response even when press and release land in the same frame.
            scope.launch {
                pulse.snapTo(1f)
                pulse.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 240f))
            }
        },
    )
}
