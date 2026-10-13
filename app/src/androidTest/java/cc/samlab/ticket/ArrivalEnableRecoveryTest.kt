package cc.samlab.ticket

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalAccess
import cc.samlab.ticket.arrival.ArrivalStore
import cc.samlab.ticket.arrival.StationArrivalBridge
import cc.samlab.ticket.arrival.StationArrivalMonitor
import cc.samlab.ticket.ui.ExpressiveDialogHost
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrivalEnableRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = ArrivalStore(context)
    private val monitor = StationArrivalMonitor.get(context)
    private val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
    private lateinit var saved: Map<String, *>
    private lateinit var activity: MainActivity
    private lateinit var bridge: StationArrivalBridge
    private val replies = mutableListOf<Pair<String, JSONObject>>()

    @Before fun launch() {
        assertTrue("需要已有精确定位、后台定位和通知权限", ArrivalAccess.ready(context))
        saved = preferences.all.toMap()
        instrumentation.runOnMainSync { monitor.stop() }
        activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            bridge = StationArrivalBridge(
                activity,
                { _, id, result -> replies.add(id to result) },
                ExpressiveDialogHost(activity),
            )
        }
    }

    @After fun restore() {
        if (!::saved.isInitialized) return
        instrumentation.runOnMainSync {
            bridge.disable("cleanup")
            activity.finish()
            val editor = preferences.edit().clear()
            saved.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()
            monitor.restore()
        }
    }

    @Test fun oneConfirmationEnablesAndCompletesOnlyOnce() {
        instrumentation.runOnMainSync { bridge.enable("first") }
        compose.onNodeWithText("继续").performClick()
        awaitCompletion { replies.any { it.first == "first" } }
        instrumentation.runOnMainSync {
            val result = replies.single { it.first == "first" }.second
            assertTrue(result.getBoolean("success"))
            assertTrue(result.getBoolean("enabled"))
            assertTrue(result.getBoolean("active"))
            assertFalse(store.enableRequested)
            bridge.onResume()
            assertEquals(1, replies.count { it.first == "first" })
        }
    }

    @Test fun acceptedRequestSurvivesBridgeRecreationWithoutSecondEnableAction() {
        instrumentation.runOnMainSync {
            store.enableRequested = true
            // 新 bridge 不持有网页请求 ID，模拟设置授权后进程恢复的状态。
            bridge.onResume()
        }
        awaitCompletion { store.enabled && store.registered && !store.enableRequested }
        instrumentation.runOnMainSync {
            assertTrue(store.enabled)
            assertTrue(store.registered)
            assertTrue(replies.isEmpty())
        }
    }

    private fun awaitCompletion(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var completed = false
            instrumentation.runOnMainSync { completed = condition() }
            if (completed) return
            Thread.sleep(100)
        }
        throw AssertionError("开启流程未完成：${store.message}")
    }
}
