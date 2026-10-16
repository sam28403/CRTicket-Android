package cc.samlab.ticket.ui.home

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun homeColorScheme(context: Context, dark: Boolean, pureBlack: Boolean = false): ColorScheme {
    return HomeColorSchemeCache.get(context.resources, dark, pureBlack) {
        buildHomeColorScheme(context, dark, pureBlack)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun buildHomeColorScheme(context: Context, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else expressiveLightColorScheme()
    }
    val tonal = if (dark) colors.copy(
        background = lerp(Color(0xFF101114), colors.primary, 0.035f),
        surface = lerp(Color(0xFF101114), colors.primary, 0.035f),
        surfaceDim = lerp(Color(0xFF0F1013), colors.primary, 0.025f),
        surfaceBright = lerp(Color(0xFF303136), colors.primary, 0.06f),
        surfaceContainer = lerp(Color(0xFF121317), colors.primary, 0.04f),
        surfaceContainerLowest = lerp(Color(0xFF1C1D21), colors.primary, 0.04f),
        surfaceContainerLow = lerp(Color(0xFF202125), colors.primary, 0.04f),
        surfaceContainerHigh = lerp(Color(0xFF27282D), colors.primary, 0.05f),
        surfaceContainerHighest = lerp(Color(0xFF303137), colors.primary, 0.06f),
    ) else colors
    return if (dark && pureBlack) tonal.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF202020),
        surfaceContainer = Color.Black,
        surfaceContainerLowest = Color(0xFF101010),
        surfaceContainerLow = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF1B1B1B),
        surfaceContainerHighest = Color(0xFF242424),
    ) else tonal
}
