package cc.samlab.ticket.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import cc.samlab.ticket.network.NetworkFeatures

@Composable
internal fun NetworkSettingsGroup(controller: NativeSettingsController) {
    if (!BuildConfig.CI_BUILD) return
    val title = stringResource(R.string.network_features_enabled)
    val enabled = NetworkFeatures.enabled
    val change: (Boolean) -> Unit = { value ->
        NetworkFeatures.setEnabled(controller.activity, value)
        controller.webView.evaluateJavascript(
            "window.dispatchEvent(new Event('crticket-network-changed'));" +
                "window.crticketTimetable?.configure()", null,
        )
    }
    SettingsGroup(title) {
        SettingsRow(title, "", 0, 1, true, { change(!enabled) }) {
            SettingsSwitch(enabled, true, title, change)
        }
    }
}
