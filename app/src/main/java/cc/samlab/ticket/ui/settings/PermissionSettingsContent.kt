package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun PermissionSettingsContent(controller: PermissionSettingsController, embedded: Boolean) {
    val state = controller.state
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            (if (embedded) Modifier else Modifier.widthIn(max = 640.dp)).fillMaxWidth()
                .testTag("permissions-content").verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            SettingsGroup(stringResource(R.string.permissions_notifications_group), first = true) {
                val count = if (state.promotedNotifications != null) 2 else 1
                SettingsRow(
                    stringResource(R.string.permissions_notifications),
                    stringResource(
                        if (state.notifications) R.string.permissions_notifications_allowed
                        else R.string.permissions_not_allowed_settings,
                    ),
                    0, count, true, controller::openNotifications,
                )
                state.promotedNotifications?.let { allowed ->
                    SettingsRow(
                        stringResource(R.string.permissions_live_updates),
                        stringResource(
                            if (allowed) R.string.permissions_live_updates_allowed
                            else R.string.permissions_not_allowed_settings,
                        ),
                        1, count, true, controller::openPromotedNotifications,
                    )
                }
            }
            SettingsGroup(stringResource(R.string.permissions_location_group)) {
                val location = when {
                    state.fineLocation -> stringResource(R.string.permissions_location_precise)
                    state.coarseLocation -> stringResource(R.string.permissions_location_approximate)
                    else -> stringResource(R.string.permissions_not_allowed)
                }
                SettingsRow(
                    stringResource(R.string.permissions_location),
                    stringResource(R.string.permissions_location_description, location),
                    0, 2, true, controller::openLocationPermissions,
                )
                SettingsRow(
                    stringResource(R.string.permissions_background_location),
                    stringResource(
                        if (state.backgroundLocation) R.string.permissions_background_location_allowed
                        else R.string.permissions_background_location_denied,
                    ),
                    1, 2, true, controller::openLocationPermissions,
                )
            }
            SettingsGroup(stringResource(R.string.permissions_background_group)) {
                SettingsRow(
                    stringResource(R.string.permissions_battery),
                    stringResource(
                        if (state.unrestrictedBattery) R.string.permissions_battery_unrestricted
                        else R.string.permissions_battery_optimized,
                    ),
                    0, 1, true, controller::openBatteryOptimization,
                )
            }
            SettingsGroup(stringResource(R.string.permissions_system_group)) {
                SettingsRow(
                    stringResource(R.string.permissions_all),
                    stringResource(R.string.permissions_all_description),
                    0, 1, true, controller::openAppPermissions,
                )
            }
        }
    }
}
