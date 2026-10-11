package cc.samlab.ticket.arrival

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import cc.samlab.ticket.ui.ExpressiveDialogHost

class ArrivalPermissions(
    private val activity: Activity,
    private val dialogs: ExpressiveDialogHost,
    private val complete: (Boolean, String) -> Unit,
) {
    companion object {
        private const val FINE = 51
        private const val BACKGROUND = 52
        private const val SETTINGS = 55
    }
    private var waiting = false
    private val preferences = activity.getSharedPreferences("arrival-permissions", Activity.MODE_PRIVATE)

    fun request() {
        waiting = true
        dialogs.show(
            title = "开启到站提醒",
            message = "CRTicket 会在应用关闭或不使用时获取位置，用于附近车站的到站通知。\n\n" +
                "请允许精确定位、始终允许定位及到站通知。位置仅在手机上匹配车站，不保存出行轨迹。\n\n" +
                "使用低功耗地理围栏，进入车站 300 米范围后提醒，可随时关闭。" +
                "系统省电策略可能让提醒延迟几分钟。",
            confirmLabel = "继续",
            onConfirm = ::foreground,
            onDecline = { finish(false, "尚未开启到站提醒") },
            onDismiss = { finish(false, "已取消开启到站提醒") },
        )
    }

    fun cancel() {
        waiting = false
        dialogs.dismiss()
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
            settings("请在“权限 → 位置信息”中允许精确定位，并选择“始终允许”。")
        }
    }

    private fun background() {
        if (!waiting) return
        if (ArrivalAccess.background(activity)) {
            finish(true, "定位权限已开启")
        } else if (Build.VERSION.SDK_INT == 29) {
            activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), BACKGROUND)
        } else {
            settings("请进入“权限 → 位置信息”，开启精确定位，并选择“始终允许”。返回应用后继续开启到站通知。")
        }
    }

    private fun settings(message: String) {
        dialogs.show(
            title = "允许后台定位",
            message = message,
            confirmLabel = "去设置",
            onConfirm = {
                try {
                    activity.startActivityForResult(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                        SETTINGS,
                    )
                } catch (_: Exception) {
                    finish(false, "无法打开设置，请手动允许始终定位")
                }
            },
            onDecline = { finish(false, "需要始终允许定位才能提供后台到站提醒") },
            onDismiss = { finish(false, "已取消开启到站提醒") },
        )
    }

    fun onRequestPermissionsResult(code: Int) {
        if (!waiting) return
        when (code) {
            FINE -> if (ArrivalAccess.fine(activity)) background()
                else finish(false, "需要精确定位，模糊位置无法识别 300 米到站范围")
            BACKGROUND -> finish(ArrivalAccess.background(activity), "请允许始终定位后重新开启")
        }
    }

    fun onActivityResult(code: Int) {
        if (code == SETTINGS && waiting) finish(ArrivalAccess.fine(activity) &&
            ArrivalAccess.background(activity), "请允许精确定位和始终定位后重新开启")
    }

    private fun finish(granted: Boolean, message: String) {
        if (!waiting) return
        waiting = false
        complete(granted, message)
    }
}
