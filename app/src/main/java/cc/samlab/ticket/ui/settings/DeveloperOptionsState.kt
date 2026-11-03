package cc.samlab.ticket.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class DeveloperOptionsState(context: Context) {
    private val preferences = context.getSharedPreferences("developer-options", Context.MODE_PRIVATE)
    var visible by mutableStateOf(preferences.getBoolean("visible", false))
        private set

    fun reveal() {
        preferences.edit().putBoolean("visible", true).apply()
        visible = true
    }
}

internal fun matchesDeveloperSearch(query: String): Boolean =
    query == "G8964" || query == "CR400AF-T-2808"
