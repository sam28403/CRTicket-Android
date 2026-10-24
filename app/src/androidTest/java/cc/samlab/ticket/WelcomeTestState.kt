package cc.samlab.ticket

import android.content.Context
import cc.samlab.ticket.ui.welcome.WelcomeState

/** Keep startup-state fixtures from leaking into other tests or the user's app. */
internal class WelcomeTestState(context: Context) : AutoCloseable {
    private val preferences = context.getSharedPreferences("welcome", Context.MODE_PRIVATE)
    private val original = preferences.all.toMap()

    init {
        WelcomeState.complete(context)
    }

    override fun close() {
        preferences.edit().clear().apply {
            original.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }.commit()
    }
}
