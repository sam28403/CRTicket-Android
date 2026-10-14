package cc.samlab.ticket.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process

/** Use an OEM's accessible per-app permission page when one is available. */
internal object AppPermissionSettings {
    fun intent(context: Context): Intent? {
        val actions = listOf(
            "android.settings.APP_PERMISSIONS_SETTINGS",
            "android.intent.action.MANAGE_APP_PERMISSIONS",
        )
        return actions.firstNotNullOfOrNull { action -> accessibleIntent(context, action) }
    }

    private fun accessibleIntent(context: Context, action: String): Intent? {
        val intent = Intent(action)
            .putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
            .putExtra("android.intent.extra.USER", Process.myUserHandle())
        val target = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }.firstOrNull { activity ->
                val system = activity.applicationInfo.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val permission = activity.permission
                system && activity.exported && (permission.isNullOrEmpty() ||
                    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
            } ?: return null
        return intent.setClassName(target.packageName, target.name)
    }
}
