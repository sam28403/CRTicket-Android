package cc.samlab.ticket.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class PermissionSettingsState(
    val notifications: Boolean,
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val backgroundLocation: Boolean,
    val promotedNotifications: Boolean?,
    val unrestrictedBattery: Boolean,
)

/** The system owns permission grants and revocations; these entries open its management UI. */
internal class PermissionSettingsController(private val activity: ComponentActivity) {
    var state by mutableStateOf(readState())
        private set

    fun refresh() {
        state = readState()
    }

    private fun granted(permission: String) =
        activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun readState(): PermissionSettingsState {
        val notifications = activity.getSystemService(NotificationManager::class.java)
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return PermissionSettingsState(
            notifications = notifications.areNotificationsEnabled(),
            fineLocation = fine,
            coarseLocation = coarse,
            backgroundLocation = (fine || coarse) && (Build.VERSION.SDK_INT < 29 ||
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
            promotedNotifications = if (Build.VERSION.SDK_INT >= 36) {
                notifications.canPostPromotedNotifications()
            } else null,
            unrestrictedBattery = activity.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(activity.packageName),
        )
    }

    fun openAppPermissions() = openPermissions("请进入“权限”，再选择需要管理的权限")

    fun openLocationPermissions() = openPermissions("请进入“权限 → 位置信息”，选择允许方式")

    private fun openPermissions(pathHint: String) {
        val direct = AppPermissionSettings.intent(activity)
        if (direct != null) {
            launch(direct, pathHint)
        } else {
            launch(appDetails())
            Toast.makeText(activity, pathHint, Toast.LENGTH_LONG).show()
        }
    }

    fun openNotifications() = launch(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
    )

    fun openPromotedNotifications() {
        if (Build.VERSION.SDK_INT >= 36) launch(
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
        ) else openNotifications()
    }

    fun openBatteryOptimization() = launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun appDetails() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${activity.packageName}"),
    )

    private fun launch(intent: Intent, fallbackHint: String? = null) {
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            runCatching { activity.startActivity(appDetails()) }.onSuccess {
                fallbackHint?.let { hint ->
                    Toast.makeText(activity, hint, Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(activity, "无法打开系统设置，请在系统设置中管理 CRTicket 权限", Toast.LENGTH_LONG).show()
            }
        }
    }
}
