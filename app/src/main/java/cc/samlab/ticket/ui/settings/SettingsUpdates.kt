package cc.samlab.ticket.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.update.updates

@Composable
internal fun SettingsUpdates() {
    val model = (LocalActivity.current as ComponentActivity).updates()
    SettingsGroup(stringResource(R.string.settings_updates)) {
        SettingsRow(
            stringResource(R.string.update_check),
            if (model.checking) stringResource(R.string.update_checking) else "",
            0, 3, model.enabled && !model.checking, { model.check() },
        )
        SettingsRow(
            stringResource(R.string.update_previews), "",
            1, 3, model.enabled, { model.changePreviews(!model.previews) },
        ) {
            SettingsSwitch(model.previews, model.enabled, stringResource(R.string.update_previews), model::changePreviews)
        }
        SettingsRow(
            stringResource(R.string.update_automatic), "",
            2, 3, model.enabled, { model.changeAutomatic(!model.automatic) },
        ) {
            SettingsSwitch(model.automatic, model.enabled, stringResource(R.string.update_automatic), model::changeAutomatic)
        }
    }
}
