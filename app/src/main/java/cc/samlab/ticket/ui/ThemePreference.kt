package cc.samlab.ticket.ui

import android.content.Context
import android.content.res.Configuration

/** Persist the user's choice separately from the last rendered appearance. */
internal object ThemePreference {
    fun resolveDark(preference: String?, systemDark: Boolean): Boolean =
        when (preference?.substringAfterLast('-')) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }

    fun isDark(context: Context): Boolean {
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val systemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return resolveDark(preferences.getString("themePreference", null), systemDark)
    }
}
