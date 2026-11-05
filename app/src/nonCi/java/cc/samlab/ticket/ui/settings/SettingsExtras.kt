package cc.samlab.ticket.ui.settings

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import org.json.JSONObject

internal class SettingsExtras(activity: ComponentActivity, web: WebView) {
    fun receive(result: JSONObject): Boolean = false
    fun close() = Unit
}

internal val extraSettingsRoutes: Set<String> = emptySet()
internal fun extraSettingsRoute(page: SettingsPage): String = "/settings"
internal fun extraSettingsPage(route: String): SettingsPage? = null

@Composable
internal fun ExtraSettingsEntry(controller: NativeSettingsController) = Unit

@Composable
internal fun ExtraSettingsContent(
    controller: NativeSettingsController,
    page: SettingsPage,
    visible: Boolean,
    embedded: Boolean,
) = Unit
