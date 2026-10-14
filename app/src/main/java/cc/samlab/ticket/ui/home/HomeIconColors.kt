package cc.samlab.ticket.ui.home

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb

internal data class HomeIconColors(val container: Color, val foreground: Color)

/** Nearby hues keep the system palette recognizable while giving each entry its own accent. */
internal fun homeIconColors(colors: ColorScheme, index: Int): HomeIconColors {
    val seed = when (index % 3) {
        0 -> colors.primary
        1 -> colors.secondary
        else -> colors.tertiary
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[0] = (hsl[0] + (index / 3 - 1) * 18f + 360f) % 360f
    hsl[1] = hsl[1].coerceAtLeast(0.55f)
    val dark = colors.surface.red + colors.surface.green + colors.surface.blue < 1.5f
    hsl[2] = if (dark) 0.74f else 0.32f
    val accent = Color(ColorUtils.HSLToColor(hsl))
    val container = lerp(colors.surfaceContainerLowest, accent, if (dark) 0.22f else 0.13f)
    val foreground = if (ColorUtils.calculateContrast(accent.toArgb(), container.toArgb()) >= 4.5) {
        accent
    } else {
        colors.onSurface
    }
    return HomeIconColors(container, foreground)
}
