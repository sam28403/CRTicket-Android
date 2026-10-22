package cc.samlab.ticket.ui.speedometer

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import cc.samlab.ticket.ui.home.projectTitleFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R
import kotlin.math.*

@Composable
internal fun SpeedGauge(speed: Double?, maximum: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val numberTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.google_sans_rounded_regular)
    }
    val fraction by animateFloatAsState(
        ((speed ?: 0.0) / maximum).toFloat().coerceIn(0f, 1f), spring(), label = "speed-needle",
    )
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val gaugeWidth = minOf(maxWidth, maxHeight * 1.18f, 480.dp)
        val gaugeScale = (gaugeWidth / 360.dp).coerceAtLeast(0.1f)
        Box(Modifier.size(gaugeWidth, gaugeWidth / 1.18f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = min(size.width / 2, size.height / 1.8f) - 30.dp.toPx() * gaugeScale
                val center = Offset(size.width / 2, size.height * 0.56f)
                val top = center - Offset(radius, radius)
                val extent = Size(radius * 2, radius * 2)
                drawArc(colors.surfaceContainerHighest, 140f, 260f, false, top, extent,
                    style = Stroke(16.dp.toPx() * gaugeScale, cap = StrokeCap.Round))
                if (fraction > 0) drawArc(colors.primary, 140f, fraction * 260, false, top, extent,
                    style = Stroke(16.dp.toPx() * gaugeScale, cap = StrokeCap.Round))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colors.onSurfaceVariant.toArgb()
                    textSize = 12.sp.toPx() * gaugeScale
                    textAlign = Paint.Align.CENTER
                    typeface = numberTypeface
                }
                for (i in 0..20) {
                    val angle = Math.toRadians((140 + i * 13).toDouble())
                    fun at(r: Float) = center + Offset(cos(angle).toFloat() * r, sin(angle).toFloat() * r)
                    drawLine(colors.onSurfaceVariant, at(radius - 16.dp.toPx() * gaugeScale),
                        at(radius - (if (i % 4 == 0) 26.dp else 21.dp).toPx() * gaugeScale), 2.dp.toPx() * gaugeScale)
                    if (i % 4 == 0) {
                        val p = at(radius - 43.dp.toPx() * gaugeScale)
                        drawContext.canvas.nativeCanvas.drawText((maximum * i / 20).toString(),
                            p.x, p.y + paint.textSize / 3, paint)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 28.dp * gaugeScale)) {
                Text(
                    speed?.let { String.format(java.util.Locale.getDefault(), "%.0f", it) } ?: "—",
                    fontFamily = projectTitleFont,
                    fontSize = 64.sp * gaugeScale,
                    fontWeight = FontWeight.Normal,
                    color = colors.primary,
                )
                Text(
                    stringResource(R.string.speed_unit),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp * gaugeScale),
                )
            }
        }
    }
}
