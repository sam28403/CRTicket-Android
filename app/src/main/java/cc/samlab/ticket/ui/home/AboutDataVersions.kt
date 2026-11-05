package cc.samlab.ticket.ui.home

import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Display the database version actually loaded by the timetable parser. */
internal class AboutDataVersions(
    private val activity: ComponentActivity,
    private val web: android.webkit.WebView,
) {
    private val preferences = activity.getSharedPreferences("data-versions", 0)
    var llt by mutableStateOf(preferences.getString("llt", "").orEmpty())
        private set
    val loading = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "llt") activity.runOnUiThread {
            llt = preferences.getString("llt", "").orEmpty()
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun refresh() {
        web.evaluateJavascript("window.crticketTimetableVersion?.()", null)
        llt = preferences.getString("llt", "").orEmpty()
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
