package cc.samlab.ticket.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R

@Composable
internal fun TimetableVisibilityGroup(controller: NativeSettingsController) {
    if (!BuildConfig.CI_BUILD) return
    val title = stringResource(R.string.developer_show_non_running_trains)
    val enabled = controller.developer.showNonRunningTrains
    val change: (Boolean) -> Unit = { value ->
        controller.developer.changeShowNonRunningTrains(value)
        controller.webView.evaluateJavascript(
            "window.dispatchEvent(new Event('crticket-timetable-visibility-changed'))", null,
        )
    }
    SettingsGroup(title) {
        SettingsRow(title, "", 0, 1, true, { change(!enabled) }) {
            SettingsSwitch(enabled, true, title, change)
        }
    }
}
