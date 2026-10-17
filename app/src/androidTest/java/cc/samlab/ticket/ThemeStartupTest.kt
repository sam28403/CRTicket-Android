package cc.samlab.ticket

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import cc.samlab.ticket.ui.home.homeColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeStartupTest {
    @Test fun nativeSurfaceUsesCurrentSystemModeBeforeWebInitialization() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("android", Context.MODE_PRIVATE)
        val original = preferences.all
        val systemDark = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        preferences.edit()
            .putString("themePreference", "classic-system")
            .putBoolean("appearanceDark", !systemDark)
            .putBoolean("appearancePureBlack", false)
            .commit()

        var startupColor: Int? = null
        var expectedColor: Int? = null
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.CREATED) {
                // CREATED runs after onCreate, before queued WebView bridge callbacks.
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                startupColor = (content.getChildAt(0).background as ColorDrawable).color
                expectedColor = homeColorScheme(activity, systemDark, false)
                    .surfaceContainer.toArgb()
            }
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        var activity: MainActivity? = null
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
            assertEquals("Native startup must ignore the stale rendered color", expectedColor, startupColor)
            check(startupColor != null) { "Activity startup was not observed" }
        } finally {
            instrumentation.runOnMainSync {
                monitor.removeLifecycleCallback(callback)
                activity?.finish()
            }
            // Restore only appearance keys, keeping unrelated app preferences intact.
            val editor = preferences.edit()
            for (key in listOf("themePreference", "appearanceDark", "appearancePureBlack")) {
                when (val value = original[key]) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    else -> editor.remove(key)
                }
            }
            editor.commit()
        }
    }
}
