package cc.samlab.ticket.arrival

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class ArrivalPermissions(private val activity: Activity, private val complete: (Boolean, String) -> Unit) {
    companion object {
        private const val FINE = 51
        private const val BACKGROUND = 52
        private const val SETTINGS = 55
    }
    private var waiting = false
    private val preferences = activity.getSharedPreferences("arrival-permissions", Activity.MODE_PRIVATE)

    fun request() {
        waiting = true
        AlertDialog.Builder(activity).setTitle("开启到站提醒")
            .setMessage("CRTicket 会在应用关闭或不使用时获取位置，用于附近车站的到站通知。" +
                "请允许精确定位、始终允许定位及到站通知。位置仅在手机上匹配车站，不保存出行轨迹。" +
                "使用低功耗地理围栏，进入约 300 米范围并短暂停留后提醒，可随时关闭。")
            .setPositiveButton("继续") { _, _ -> foreground() }
            .setNegativeButton("暂不开启") { _, _ -> finish(false, "尚未开启到站提醒") }
            .setOnCancelListener { finish(false, "已取消开启到站提醒") }.show()
    }

    fun cancel() { waiting = false }

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
        AlertDialog.Builder(activity).setTitle("允许后台定位").setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                try {
                    activity.startActivityForResult(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${activity.packageName}")), SETTINGS)
                } catch (_: Exception) { finish(false, "无法打开设置，请手动允许始终定位") }
            }
            .setNegativeButton("暂不开启") { _, _ -> finish(false, "需要始终允许定位才能提供后台到站提醒") }
            .setOnCancelListener { finish(false, "已取消开启到站提醒") }.show()
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
