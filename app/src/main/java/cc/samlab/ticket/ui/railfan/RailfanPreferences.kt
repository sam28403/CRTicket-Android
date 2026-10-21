package cc.samlab.ticket.ui.railfan

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal object RailfanPreferences {
    private const val KEY = "railfanToolsEnabled"

    private fun preferences(context: Context) =
        context.getSharedPreferences("android", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY, enabled).apply()
    }

    @Composable
    fun rememberEnabled(): State<Boolean> {
        val context = LocalContext.current.applicationContext
        val state = remember(context) { mutableStateOf(enabled(context)) }
        DisposableEffect(context) {
            val preferences = preferences(context)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY) state.value = enabled(context)
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            state.value = enabled(context)
            onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }
}
