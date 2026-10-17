package cc.samlab.ticket.ui.monitor

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cc.samlab.ticket.monitor.TicketSeats
import org.json.JSONObject

internal data class MonitorCardColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val muted: Color,
    val divider: Color,
)

internal fun monitorCardColors(seats: JSONObject, colors: ColorScheme): MonitorCardColors {
    val known = TicketSeats.columns.keys.filter {
        seats.optString(it) !in listOf("", "--", "—", "*", "null")
    }
    val available = known.filter { TicketSeats.available(seats.optString(it)) }
    val dark = colors.surface.luminance() < 0.5f
    val container: Color
    val content: Color
    val accent: Color
    when {
        known.isNotEmpty() && available.isEmpty() -> {
            container = colors.errorContainer
            content = colors.onErrorContainer
            accent = colors.error
        }
        available == listOf("standing") -> {
            container = if (dark) Color(0xFF4B2800) else Color(0xFFFFDCC3)
            content = if (dark) Color(0xFFFFDCC3) else Color(0xFF321300)
            accent = if (dark) Color(0xFFFFB780) else Color(0xFF924C00)
        }
        else -> return MonitorCardColors(
            colors.surfaceContainerLowest, colors.onSurface, colors.primary,
            colors.onSurfaceVariant, colors.outlineVariant,
        )
    }
    return MonitorCardColors(
        container, content, accent,
        content.copy(alpha = 0.78f), content.copy(alpha = 0.2f),
    )
}
