package cc.samlab.ticket.ui.welcome

import android.content.Context
import cc.samlab.ticket.BuildConfig

internal object WelcomeState {
    private const val TERMS_VERSION = 1
    val version: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    fun needsTerms(context: Context): Boolean =
        context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
            .getInt("terms", 0) != TERMS_VERSION

    fun required(context: Context): Boolean = needsTerms(context) ||
        context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
            .getString("version", null) != version

    fun complete(context: Context) {
        context.getSharedPreferences("welcome", Context.MODE_PRIVATE).edit()
            .putInt("terms", TERMS_VERSION)
            .putString("version", version)
            .apply()
    }
}
