package cc.samlab.ticket.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import cc.samlab.ticket.MainActivity

class TrainTrackingNotifications(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "train-tracking"
        const val NOTIFICATION_ID = 2
        private const val UPDATE_GROUP = "train-tracking-updates"
        const val OPEN_TRACKING = "openTrainTracking"
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "列车追踪", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "列车发车、到站和终到状态"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    fun enabled(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun isActive(): Boolean = manager.activeNotifications.any {
        it.id == NOTIFICATION_ID && it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    fun build(timeline: TrainTimeline, now: Long, quietUpdate: Boolean = false): Notification {
        val status = timeline.status(now)
        val stateIcon = Icon.createWithResource(context, TrainTrackingIcons.forPhase(status.phase))
        val content = TrainTrackingText.content(timeline, now)
        val route = Uri.Builder().path("/train")
            .appendQueryParameter("train", timeline.queryTrain)
            .appendQueryParameter("date", timeline.date)
            .appendQueryParameter("source", timeline.querySource)
            .build().toString()
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_TRACKING, route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context, NOTIFICATION_ID,
            Intent(context, StopTrainTrackingReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(TrainTrackingIcons.forPhase(status.phase))
            .setLargeIcon(stateIcon)
            .setContentTitle(TrainNotificationFormatting.prominent(content.title))
            .setContentText(TrainNotificationFormatting.prominent(content.text))
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .setOnlyAlertOnce(true)
            .setGroup(UPDATE_GROUP)
            .setGroupAlertBehavior(if (quietUpdate) {
                Notification.GROUP_ALERT_SUMMARY
            } else {
                Notification.GROUP_ALERT_ALL
            })
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.BigTextStyle()
                .bigText(TrainNotificationFormatting.prominent(content.text)))
        builder.addAction(Notification.Action.Builder(null, "停止追踪", stop).build())
        if (Build.VERSION.SDK_INT >= 31) {
            // Show the first foreground notice while the user is still in the app.
            // A deferred first delivery can otherwise pulse after entering AOD.
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            // The user explicitly starts tracking, including before departure.
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                builder.setRequestPromotedOngoing(true)
            }
            // ProgressStyle limits the body to two visual lines and can hide the next stop.
            // Keep BigTextStyle at stations so both arrival and departure details remain visible.
            if (status.phase != TrainPhase.AT_STATION) {
                builder.setStyle(
                    Notification.ProgressStyle()
                        .setProgressTrackerIcon(stateIcon)
                        .setProgressSegments(listOf(
                            Notification.ProgressStyle.Segment(100)
                                .setColor(Color.rgb(64, 158, 255)),
                        ))
                        .setProgress(status.progress)
                        .setStyledByProgress(true)
                )
            }
            status.nextEvent?.let { builder.setWhen(it) }
            // Station names and explicit countdowns take priority over automatic time text.
            builder.setShortCriticalText(content.chip)
        } else {
            builder.setProgress(100, status.progress, false)
        }
        return builder.build()
    }

    fun publish(notification: Notification) {
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }
}
