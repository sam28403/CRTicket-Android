package cc.samlab.ticket.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object NotificationAccess {
    fun enabled(context: Context, channelId: String): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance !=
                NotificationManager.IMPORTANCE_NONE
    }
}
