package cc.samlab.ticket

import android.view.accessibility.AccessibilityEvent
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalAccess
import cc.samlab.ticket.arrival.ArrivalStore
import cc.samlab.ticket.arrival.StationArrivalMonitor
import cc.samlab.ticket.web.WebViewVersionPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ArrivalActivationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun switchReflectsRegistrationAndFailedActivationCanRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Requires working GMS and existing location permissions", ArrivalAccess.ready(context))
        val store = ArrivalStore(context)
        instrumentation.runOnMainSync {
            StationArrivalMonitor.get(context).stop()
            store.enableRequested = false
        }
        val welcome = WelcomeTestState(context)
        val fixture = TabletPageFixture(compose, dismissWebViewWarning =
            WebViewVersionPolicy.isUnsupported(WebView.getCurrentWebViewPackage()?.versionName))
        val toast = AtomicReference("")
        instrumentation.uiAutomation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.packageName?.toString() == context.packageName) {
                toast.set(event.text.joinToString(""))
            }
        }
        try {
            fixture.navigate("/settings")
            compose.onNode(hasText(context.getString(R.string.settings_arrival)) and hasClickAction())
                .performScrollTo().performClick()
            val toggle = compose.onNodeWithContentDescription(context.getString(R.string.settings_arrival_switch))
            toggle.assertIsOff().performClick()
            compose.onNodeWithText(context.getString(R.string.action_continue)).performClick()
            if (store.enableRequested) {
                toggle.assertIsOff()
                compose.onNodeWithTag("arrival-switch-loading").assertIsDisplayed().assertIsNotEnabled()
                fixture.capture("arrival-loading-detail")
                fixture.shell("input keyevent KEYCODE_BACK")
                compose.onNodeWithContentDescription(context.getString(R.string.settings_arrival_switch))
                    .performScrollTo().assertIsDisplayed()
                fixture.capture("arrival-loading-settings")
                compose.onNode(hasText(context.getString(R.string.settings_arrival)) and hasClickAction())
                    .performClick()
                compose.onNodeWithText(context.getString(R.string.arrival_privacy_description))
                    .performTouchInput { click(center) }
                if (store.enableRequested) toggle.assertIsOff()
            }
            compose.waitUntil(35_000) {
                !store.enableRequested && (store.registered || toast.get().isNotBlank())
            }
            assertFalse(store.enableRequested)
            compose.onNodeWithTag("arrival-switch-loading").assertDoesNotExist()
            if (store.registered) {
                toggle.assertIsOn()
                assertTrue(store.enabled)
                assertTrue(ArrivalAccess.ready(context))
            } else {
                toggle.assertIsOff()
                assertFalse(store.enabled)
                assertEquals(store.message, toast.get())
                toggle.performClick()
                compose.onNodeWithText(context.getString(R.string.arrival_permission_title)).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.action_not_now)).performClick()
                assertFalse(store.enableRequested)
                toggle.assertIsOff()
            }
            fixture.capture("arrival-activation-complete")
        } finally {
            instrumentation.uiAutomation.setOnAccessibilityEventListener(null)
            fixture.close()
            welcome.close()
        }
    }
}
