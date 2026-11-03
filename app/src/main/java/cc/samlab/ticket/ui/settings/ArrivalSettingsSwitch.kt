package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cc.samlab.ticket.ui.components.AppSwitch
import cc.samlab.ticket.R
import org.json.JSONObject

@Composable
internal fun ArrivalSettingsSwitch(controller: NativeSettingsController, enabled: Boolean) {
    val state = controller.state
    val change: (Boolean) -> Unit = {
        controller.request("arrival", JSONObject().put("enabled", it))
    }
    Box {
        val label = stringResource(R.string.settings_arrival_switch)
        AppSwitch(
            checked = state.arrivalSupported && state.arrivalEnabled,
            onCheckedChange = change,
            enabled = enabled && state.arrivalSupported && !state.arrivalStarting,
            loading = state.arrivalStarting,
            modifier = Modifier.semantics { contentDescription = label }
                .testTag(if (state.arrivalStarting) "arrival-switch-loading" else "arrival-switch"),
        )
        if (enabled && !state.arrivalSupported) {
            Box(Modifier.matchParentSize().clickable { change(true) })
        }
    }
}
