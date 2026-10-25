package cc.samlab.ticket.update

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri

internal sealed interface UpdateDownloadResult {
    data class Started(val id: Long) : UpdateDownloadResult
    data class Ready(val id: Long) : UpdateDownloadResult
    data object InProgress : UpdateDownloadResult
}

internal class UpdateDownloads(private val context: Context) {
    private val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    fun enqueue(update: AppRelease): UpdateDownloadResult {
        check(cc.samlab.ticket.BuildConfig.UPDATE_ENABLED) { "Updates disabled for this build" }
        val url = requireNotNull(update.downloadUrl)
        require(isReleaseDownload(url))
        val manager = requireNotNull(context.getSystemService(DownloadManager::class.java))
        val key = "download:${update.tag}"
        val previous = preferences.getLong(key, -1)
        if (previous != -1L) {
            manager.query(DownloadManager.Query().setFilterById(previous))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = manager.getUriForDownloadedFile(previous)
                            val readable = uri != null && runCatching {
                                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
                            }.getOrDefault(false)
                            if (readable) return UpdateDownloadResult.Ready(previous)
                        }
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PAUSED -> return UpdateDownloadResult.InProgress
                    }
                }
            }
        }
        val safeTag = update.tag.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val filename = "CRTicket-$safeTag-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(url.toUri())
            .setTitle("CRTicket ${update.tag}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= 29) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        }
        val id = manager.enqueue(request)
        preferences.edit().putLong(key, id).apply()
        return UpdateDownloadResult.Started(id)
    }
}
