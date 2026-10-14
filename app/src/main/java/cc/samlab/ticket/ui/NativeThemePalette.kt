package cc.samlab.ticket.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import java.util.Locale

/** Match Web surfaces to the same system-derived palette as the native pages. */
internal fun nativeThemePalette(colors: ColorScheme): JSONObject {
    fun Color.hex() = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)
    return JSONObject().apply {
        val values = mapOf(
            "--app-page" to colors.surfaceContainer,
            "--app-surface" to colors.surfaceContainerLowest,
            "--app-text" to colors.onSurface,
            "--app-muted" to colors.onSurfaceVariant,
            "--app-border" to colors.outlineVariant,
            "--app-hover" to colors.surfaceContainerHigh,
            "--app-link" to colors.primary,
            "--app-primary-button" to colors.primary,
            "--el-bg-color" to colors.surfaceContainerLowest,
            "--el-bg-color-page" to colors.surfaceContainer,
            "--el-bg-color-overlay" to colors.surfaceContainerHigh,
            "--el-text-color-primary" to colors.onSurface,
            "--el-text-color-regular" to colors.onSurface,
            "--el-text-color-secondary" to colors.onSurfaceVariant,
            "--el-text-color-placeholder" to colors.onSurfaceVariant,
            "--el-border-color" to colors.outlineVariant,
            "--el-border-color-light" to colors.outlineVariant,
            "--el-fill-color-blank" to colors.surfaceContainerLowest,
            "--el-fill-color-light" to colors.surfaceContainerHigh,
            "--el-color-primary" to colors.primary,
        )
        values.forEach { (key, color) -> put(key, color.hex()) }
    }
}
