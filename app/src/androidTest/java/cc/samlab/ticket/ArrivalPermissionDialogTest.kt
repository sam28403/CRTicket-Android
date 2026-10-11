package cc.samlab.ticket

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalPermissions
import cc.samlab.ticket.ui.ExpressiveDialogHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrivalPermissionDialogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private lateinit var dialogs: ExpressiveDialogHost

    @Before fun launch() {
        activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            dialogs = ExpressiveDialogHost(activity)
        }
        instrumentation.waitForIdleSync()
    }

    @After fun close() {
        instrumentation.runOnMainSync { activity.finish() }
    }

    @Test fun decliningAndBackCompleteArrivalRequestOnceWithoutLocationPermission() {
        val results = mutableListOf<Pair<Boolean, String>>()
        lateinit var permissions: ArrivalPermissions
        instrumentation.runOnMainSync {
            permissions = ArrivalPermissions(activity, dialogs) { granted, message ->
                results.add(granted to message)
            }
            permissions.request()
        }
        compose.onNodeWithText("开启到站提醒").assertIsDisplayed()
        compose.onNodeWithText("暂不开启").performClick()
        compose.runOnIdle {
            assertEquals(listOf(false to "尚未开启到站提醒"), results)
            permissions.request()
        }
        compose.onNodeWithText("开启到站提醒").assertIsDisplayed()
        pressBack()
        compose.runOnIdle {
            assertEquals(
                listOf(false to "尚未开启到站提醒", false to "已取消开启到站提醒"),
                results,
            )
        }
    }

    @Test fun confirmationCanOpenNextDialogAndCancellationRemovesIt() {
        var confirmations = 0
        instrumentation.runOnMainSync {
            dialogs.show(
                title = "开启到站提醒",
                message = "定位用途说明",
                confirmLabel = "继续",
                onConfirm = {
                    confirmations++
                    dialogs.show(
                        title = "允许后台定位",
                        message = "请选择始终允许",
                        confirmLabel = "去设置",
                        onConfirm = {},
                        onDecline = {},
                    )
                },
                onDecline = {},
            )
        }
        compose.onNodeWithText("继续").performClick()
        compose.onNodeWithText("允许后台定位").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, confirmations)
            dialogs.dark = true
        }
        compose.onNodeWithText("去设置").assertIsDisplayed()
        compose.runOnIdle { dialogs.dismiss() }
        compose.onNodeWithText("允许后台定位").assertDoesNotExist()
    }
}
