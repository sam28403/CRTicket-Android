package cc.samlab.ticket.arrival

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import cc.samlab.ticket.R
import cc.samlab.ticket.permissions.SettingsReturn
import cc.samlab.ticket.ui.ExpressiveDialogHost

class ArrivalPermissions(
    private val activity: Activity,
    private val dialogs: ExpressiveDialogHost,
    private val accepted: () -> Unit = {},
    private val complete: (Boolean, String) -> Unit,
) {
    companion object {
        private const val FINE = 51
        private const val BACKGROUND = 52
        private const val SETTINGS = 55
    }
    private var waiting = false
    private val settingsReturn = SettingsReturn()
    val inProgress: Boolean get() = waiting
    private val preferences = activity.getSharedPreferences("arrival-permissions", Activity.MODE_PRIVATE)

    fun request() {
        waiting = true
        dialogs.show(
            title = activity.getString(R.string.arrival_permission_title),
            message = activity.getString(R.string.arrival_permission_message),
            confirmLabel = activity.getString(R.string.action_continue),
            onConfirm = {
                accepted()
                foreground()
            },
            onDecline = { finish(false, activity.getString(R.string.arrival_not_enabled)) },
            onDismiss = { finish(false, activity.getString(R.string.arrival_cancelled)) },
        )
    }

    fun cancel() {
        waiting = false
        settingsReturn.cancel()
        dialogs.dismiss()
    }

    // 授权更改可能重建进程，已确认用途的请求无需再次显示开启说明。
    fun resume() {
        if (waiting) return
        waiting = true
        foreground()
    }

    private fun foreground() {
        if (!waiting) return
        if (ArrivalAccess.fine(activity)) {
            background()
        } else if (!preferences.getBoolean("fineAsked", false) ||
            activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            preferences.edit().putBoolean("fineAsked", true).apply()
            activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION), FINE)
        } else {
            settings(activity.getString(R.string.arrival_precise_settings))
        }
    }

    private fun background() {
        if (!waiting) return
        if (ArrivalAccess.background(activity)) {
            finish(true, activity.getString(R.string.arrival_location_enabled))
        } else if (Build.VERSION.SDK_INT == 29) {
            activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), BACKGROUND)
        } else {
            settings(activity.getString(R.string.arrival_background_settings))
        }
    }

    private fun settings(message: String) {
        dialogs.show(
            title = activity.getString(R.string.arrival_background_title),
            message = message,
            confirmLabel = activity.getString(R.string.action_open_settings),
            onConfirm = {
                try {
                    settingsReturn.launch()
                    activity.startActivityForResult(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                        SETTINGS,
                    )
                } catch (_: Exception) {
                    finish(false, activity.getString(R.string.arrival_settings_failed))
                }
            },
            onDecline = { finish(false, activity.getString(R.string.arrival_background_required)) },
            onDismiss = { finish(false, activity.getString(R.string.arrival_cancelled)) },
        )
    }

    fun onRequestPermissionsResult(code: Int) {
        if (!waiting) return
        when (code) {
            FINE -> if (ArrivalAccess.fine(activity)) background()
                else finish(false, activity.getString(R.string.arrival_precise_required))
            BACKGROUND -> finish(
                ArrivalAccess.background(activity),
                activity.getString(R.string.arrival_background_required),
            )
        }
    }

    fun onPause() = settingsReturn.onPause()

    fun onResume() {
        if (settingsReturn.onResume() && waiting) {
            finish(
                ArrivalAccess.fine(activity) && ArrivalAccess.background(activity),
                activity.getString(R.string.arrival_location_required),
            )
        }
    }

    private fun finish(granted: Boolean, message: String) {
        if (!waiting) return
        waiting = false
        settingsReturn.cancel()
        complete(granted, message)
    }
}
