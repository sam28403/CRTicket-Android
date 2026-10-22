package cc.samlab.ticket.ui.speedometer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun SpeedometerGuide(confirm: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PlacementAnimation()
        Text(stringResource(R.string.speed_guide_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.speed_guide_placement), style = MaterialTheme.typography.bodyLarge)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Info, null)
                Text(stringResource(R.string.speed_guide_mesh))
                Text(stringResource(R.string.speed_guide_tunnel))
                Text(stringResource(R.string.speed_guide_pause))
            }
        }
        Button(onClick = confirm, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.speed_confirm))
        }
    }
}

@Composable
private fun PlacementAnimation() {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "phone-placement")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "stand-phone-upright",
    )
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                val window = Offset(size.width * 0.48f, 24.dp.toPx())
                drawRoundRect(colors.onPrimaryContainer.copy(alpha = 0.15f), window,
                    Size(size.width * 0.44f, size.height - 48.dp.toPx()), CornerRadius(28.dp.toPx()))
                drawLine(colors.onPrimaryContainer.copy(alpha = 0.3f),
                    Offset(window.x, size.height * 0.7f),
                    Offset(size.width * 0.92f, size.height * 0.7f), 4.dp.toPx())
                val center = Offset(size.width * (0.3f + progress * 0.28f), size.height * 0.53f)
                rotate(28f * (1 - progress), center) {
                    val width = 62.dp.toPx()
                    val height = 120.dp.toPx()
                    val top = center - Offset(width / 2, height / 2)
                    drawRoundRect(colors.primary, top, Size(width, height), CornerRadius(16.dp.toPx()))
                    drawRoundRect(colors.surface, top + Offset(6.dp.toPx(), 10.dp.toPx()),
                        Size(width - 12.dp.toPx(), height - 20.dp.toPx()), CornerRadius(10.dp.toPx()))
                    drawCircle(colors.primary, 15.dp.toPx(), center, style = Stroke(4.dp.toPx()))
                    drawLine(colors.primary, center, center + Offset(9.dp.toPx(), -9.dp.toPx()), 3.dp.toPx())
                }
            }
        }
    }
}
