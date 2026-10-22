package cc.samlab.ticket.ui.speedometer

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

@Composable
internal fun speedometerColorScheme(signalLost: Boolean): androidx.compose.material3.ColorScheme {
    val accent by animateColorAsState(
        if (signalLost) Color(0xFFFFB4AB) else Color(0xFFB4F4C0), label = "signal-accent",
    )
    val container by animateColorAsState(
        if (signalLost) Color(0xFF542B2A) else Color(0xFF253D2C), label = "signal-container",
    )
    return darkColorScheme(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerLowest = if (signalLost) Color(0xFF211616) else Color(0xFF141916),
        primary = accent,
        onPrimary = if (signalLost) Color(0xFF590B0B) else Color(0xFF003916),
        secondaryContainer = container,
        onSecondaryContainer = if (signalLost) Color(0xFFFFDAD5) else Color(0xFFD0F5D4),
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFD0D8D0),
        surfaceContainerHighest = if (signalLost) Color(0xFF59413F) else Color(0xFF404A42),
    )
}
