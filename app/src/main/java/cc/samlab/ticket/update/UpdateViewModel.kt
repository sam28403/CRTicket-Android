package cc.samlab.ticket.update

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import java.util.concurrent.Executors

internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val repository = ReleaseRepository()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var manualFeedback = false
    private var cleared = false
    val enabled = BuildConfig.UPDATE_ENABLED
    var installDownloadId by mutableStateOf<Long?>(null)
        private set
    var previews by mutableStateOf(enabled && preferences.getBoolean("previews", false))
        private set
    var automatic by mutableStateOf(enabled && preferences.getBoolean("automatic", true))
        private set
    var checking by mutableStateOf(false)
        private set
    var release by mutableStateOf<AppRelease?>(null)
        private set

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "previews" || key == "automatic") {
            main.post {
                if (!cleared) {
                    previews = enabled && preferences.getBoolean("previews", false)
                    automatic = enabled && preferences.getBoolean("automatic", true)
                }
            }
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun changePreviews(value: Boolean) {
        if (!enabled) return
        previews = value
        preferences.edit().putBoolean("previews", value).apply()
    }

    fun changeAutomatic(value: Boolean) {
        if (!enabled) return
        automatic = value
        preferences.edit().putBoolean("automatic", value).apply()
    }

    fun onStartup() {
        if (!enabled) return
        if (started) return
        started = true
        if (automatic) check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (!enabled) return
        manualFeedback = manualFeedback || manual
        if (checking) return
        checking = true
        val channel = previews
        worker.execute {
            val result = runCatching {
                if (BuildConfig.UPDATE_TEST_TAG.isNotEmpty()) {
                    repository.release(BuildConfig.UPDATE_TEST_TAG)
                } else repository.check(channel, BuildConfig.VERSION_NAME)
            }
            main.post {
                if (cleared) return@post
                checking = false
                if (channel != previews || (!manualFeedback && !automatic)) {
                    val retry = manualFeedback || automatic
                    val feedback = manualFeedback
                    manualFeedback = false
                    if (retry) check(feedback)
                    return@post
                }
                result.onSuccess {
                    release = it
                    if (it == null && manualFeedback) toast(R.string.update_none)
                }.onFailure {
                    if (manualFeedback) toast(R.string.update_check_failed)
                }
                manualFeedback = false
            }
        }
    }

    fun dismiss() {
        release = null
    }

    fun download() {
        if (!enabled) return
        val update = release ?: return
        val url = update.downloadUrl
        if (url == null || !isReleaseDownload(url)) {
            toast(R.string.update_no_apk)
            return
        }
        runCatching {
            when (val result = UpdateDownloads(getApplication()).enqueue(update)) {
                is UpdateDownloadResult.Started -> {
                    toast(R.string.update_download_started)
                    dismiss()
                }
                is UpdateDownloadResult.Ready -> {
                    installDownloadId = result.id
                    dismiss()
                }
                UpdateDownloadResult.InProgress -> {
                    toast(R.string.update_download_exists)
                    dismiss()
                }
            }
        }.onFailure { toast(R.string.update_download_failed) }
    }

    fun consumeInstallRequest() {
        installDownloadId = null
    }
    private fun toast(message: Int) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        cleared = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        worker.shutdownNow()
        main.removeCallbacksAndMessages(null)
    }
}
