package cc.samlab.ticket.ui.settings

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.network.NetworkFeatures
import org.json.JSONObject

internal class SettingsExtras(activity: ComponentActivity, web: WebView) {
    val developer = DeveloperOptionsState(activity)
    val timetable = TimetableSettingsController(activity, web)
    val debugTickets = DebugTicketsController(activity, web)

    init { web.addJavascriptInterface(CiSettingsBridge(activity), "CRTicketDeveloper") }

    fun receive(result: JSONObject): Boolean {
        if (!result.optString("id").startsWith("timetable:")) return false
        timetable.receive(result)
        return true
    }

    fun close() = timetable.close()
}

internal val NativeSettingsController.developer get() = extras.developer
internal val NativeSettingsController.timetable get() = extras.timetable
internal val NativeSettingsController.debugTickets get() = extras.debugTickets

internal val extraSettingsRoutes = setOf(
    "/settings/developer", "/settings/developer/tickets", "/settings/developer/timetable",
)

internal fun extraSettingsRoute(page: SettingsPage): String = when (page) {
    SettingsPage.DEVELOPER -> "/settings/developer"
    SettingsPage.DEBUG_TICKETS -> "/settings/developer/tickets"
    SettingsPage.LLT_IMPORT -> "/settings/developer/timetable"
    else -> "/settings"
}

internal fun extraSettingsPage(route: String): SettingsPage? = when (route) {
    "/settings/developer" -> SettingsPage.DEVELOPER
    "/settings/developer/tickets" -> SettingsPage.DEBUG_TICKETS
    "/settings/developer/timetable" -> SettingsPage.LLT_IMPORT
    else -> null
}

@Composable
internal fun ExtraSettingsEntry(controller: NativeSettingsController) {
    if (!controller.developer.visible) return
    SettingsGroup(stringResource(R.string.settings_developer)) {
        SettingsRow(stringResource(R.string.settings_developer), "", 0, 1, true, controller.ui::openDeveloper)
    }
}

@Composable
internal fun ExtraSettingsContent(
    controller: NativeSettingsController,
    page: SettingsPage,
    visible: Boolean,
    embedded: Boolean,
) {
    when (page) {
        SettingsPage.LLT_IMPORT -> TimetableImportContent(controller.timetable, visible)
        SettingsPage.DEBUG_TICKETS -> DebugTicketsContent(controller.debugTickets, visible)
        SettingsPage.DEVELOPER -> {
            LaunchedEffect(Unit) { controller.developer.reveal() }
            SettingsPanel(rememberScrollState(), "settings-developer", embedded) {
                SettingsGroup(stringResource(R.string.debug_tickets_title), first = true) {
                    SettingsRow(stringResource(R.string.debug_tickets_title), "", 0, 1, true,
                        controller.ui::openDebugTickets)
                }
                TimetableVisibilityGroup(controller)
                NetworkSettingsGroup(controller)
                TimetableDeveloperGroup(controller.timetable, controller.ui::openLltImport)
                SettingsDiagnostics()
            }
        }
        else -> Unit
    }
}

internal class CiSettingsBridge(private val activity: ComponentActivity) {
    private val preferences = activity.getSharedPreferences("developer-options", 0)
    @JavascriptInterface fun networkEnabled(): Boolean = NetworkFeatures.isAllowed()
    @JavascriptInterface fun lltAutoUpdate(): Boolean =
        NetworkFeatures.isAllowed() && preferences.getBoolean("lltAutoUpdate", true)
    @JavascriptInterface fun showNonRunningTrains(): Boolean =
        preferences.getBoolean("showNonRunningTrains", false)
}
