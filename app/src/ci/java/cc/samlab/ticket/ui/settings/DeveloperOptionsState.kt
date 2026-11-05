package cc.samlab.ticket.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class DeveloperOptionsState(context: Context) {
    private val preferences = context.getSharedPreferences("developer-options", Context.MODE_PRIVATE)
    var visible by mutableStateOf(preferences.getBoolean("visible", false))
        private set

    var showNonRunningTrains by mutableStateOf(preferences.getBoolean("showNonRunningTrains", false))
        private set

    fun changeShowNonRunningTrains(value: Boolean) {
        if (!cc.samlab.ticket.BuildConfig.CI_BUILD) return
        preferences.edit().putBoolean("showNonRunningTrains", value).apply()
        showNonRunningTrains = value
    }

    fun reveal() {
        preferences.edit().putBoolean("visible", true).apply()
        visible = true
    }
}

internal fun matchesDeveloperSearch(query: String): Boolean =
    query == "G8964" || query == "CR400AF-T-2808"
