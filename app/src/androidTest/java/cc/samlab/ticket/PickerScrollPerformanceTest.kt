package cc.samlab.ticket

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inspector.WindowInspector
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.components.ExpressiveDatePickerDialog
import cc.samlab.ticket.ui.history.HistoryTimePicker
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PickerScrollPerformanceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test fun reportDateAndTimeWheelFrames() {
        val welcome = WelcomeTestState(InstrumentationRegistry.getInstrumentation().targetContext)
        val page = TabletPageFixture(compose)
        val picker = mutableStateOf("")
        val initialDate = LocalDate.of(2026, 11, 18)
        val initialDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        var selectedDate: Long? = null
        var selectedTime: String? = null
        try {
            page.instrumentation.runOnMainSync {
                page.activity.findViewById<ComposeView>(R.id.expressive_navigation_view).apply {
                    visibility = View.VISIBLE
                    setContent {
                        MaterialExpressiveTheme {
                            when (picker.value) {
                                "date" -> ExpressiveDatePickerDialog(
                                    state = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis),
                                    onSelect = {
                                        selectedDate = it
                                        picker.value = ""
                                    },
                                    onDismiss = { picker.value = "" },
                                )
                                "time" -> HistoryTimePicker("08:45", { picker.value = "" }) {
                                    selectedTime = it
                                    picker.value = ""
                                }
                            }
                        }
                    }
                }
            }
            for (mode in listOf("date", "time")) {
                compose.runOnIdle { picker.value = mode }
                val wheel = if (mode == "date") {
                    compose.onNodeWithTag("date-picker-wheel-toggle").performClick()
                    compose.onNodeWithTag("date-wheel-day")
                } else {
                    compose.onAllNodes(
                        hasAnyAncestor(hasTestTag("history-time-scroll")) and
                            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                        useUnmergedTree = true,
                    )[0]
                }
                val bounds = wheel.fetchSemanticsNode().boundsInWindow
                measure(page.instrumentation, mode, bounds)
                val original = wheel.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
                val started = System.currentTimeMillis()
                wheel.performTouchInput {
                    swipeWithVelocity(center, Offset(center.x, center.y - height / 3f), endVelocity = height * 9f)
                }
                val settled = wheel.fetchSemanticsNode().config
                val advanced = (settled[SemanticsProperties.ProgressBarRangeInfo].current - original).toInt()
                assertTrue("$mode fast swipe advanced $advanced pages", advanced >= 3)
                page.instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "PICKER_FLING $mode pages=$advanced start=$started end=${System.currentTimeMillis()}\n")
                })
                val chosen = settled[SemanticsProperties.StateDescription]
                val value = NumberFormat.getIntegerInstance(page.activity.resources.configuration.locales[0])
                    .parse(chosen)?.toInt() ?: error("The wheel did not expose a selected number")
                if (mode == "date") {
                    compose.onNodeWithTag("date-picker-wheel-toggle").performClick()
                } else {
                    val toggle = compose.onNodeWithTag("history-time-mode-toggle")
                    toggle.performClick()
                    compose.onNodeWithTag("history-time-input").assertIsDisplayed()
                    toggle.performClick()
                }
                compose.onNodeWithText(page.activity.getString(R.string.action_confirm)).performClick()
                compose.runOnIdle {
                    if (mode == "date") {
                        val expected = initialDate.withDayOfMonth(value).atStartOfDay()
                            .toInstant(ZoneOffset.UTC).toEpochMilli()
                        assertEquals(expected, selectedDate)
                    } else {
                        assertEquals("%02d:45".format(Locale.ROOT, value), selectedTime)
                    }
                }
            }
        } finally {
            page.close()
            welcome.close()
        }
    }

    private fun measure(instrumentation: Instrumentation, mode: String, bounds: Rect) {
        val origin = IntArray(2)
        lateinit var recorder: FramePerformanceRecorder
        instrumentation.runOnMainSync {
            val window = WindowInspector.getGlobalWindowViews().firstNotNullOf(::composeDialogWindow)
            window.decorView.getLocationOnScreen(origin)
            recorder = FramePerformanceRecorder(window).also { it.attach() }
        }
        val x = origin[0] + bounds.center.x
        val y = origin[1] + bounds.center.y
        val distance = bounds.height * 0.36f
        try {
            Thread.sleep(500)
            swipe(instrumentation, x, y, distance)
            swipe(instrumentation, x, y, -distance)
            repeat(3) { sample ->
                recorder.reset()
                repeat(4) { swipe(instrumentation, x, y, if (it % 2 == 0) distance else -distance) }
                val summary = recorder.summary()
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "PICKER_FRAMES $mode $sample $summary\n")
                })
                assertTrue(summary.getInt("frames") >= 20)
            }
        } finally {
            instrumentation.runOnMainSync { recorder.close() }
        }
    }

    private fun composeDialogWindow(view: View): Window? {
        if (view is DialogWindowProvider) return view.window
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                composeDialogWindow(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun swipe(instrumentation: Instrumentation, x: Float, center: Float, distance: Float) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, center + distance)
        repeat(20) { index ->
            Thread.sleep(16)
            send(MotionEvent.ACTION_MOVE, center + distance - distance * (index + 1) / 10)
        }
        send(MotionEvent.ACTION_UP, center - distance)
        Thread.sleep(400)
    }
}
