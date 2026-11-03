package cc.samlab.ticket

import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.arrival.ArrivalAccess
import cc.samlab.ticket.arrival.ArrivalStore
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ArrivalUnavailableDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun unavailableServicesBlockBothSwitchesAndShowToast() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeFalse("Requires unavailable Google Play services", ArrivalAccess.supported(context))
        val store = ArrivalStore(context)
        val originalEnabled = store.enabled
        val originalRequested = store.enableRequested
        store.enabled = false
        store.enableRequested = false
        val welcome = WelcomeTestState(context)
        val fixture = TabletPageFixture(compose, dismissWebViewWarning =
            cc.samlab.ticket.web.WebViewVersionPolicy.isUnsupported(
                android.webkit.WebView.getCurrentWebViewPackage()?.versionName,
            ))
        val expected = context.getString(R.string.arrival_google_services_required)
        val toast = AtomicReference(CountDownLatch(1))
        instrumentation.uiAutomation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.text.any { it.toString() == expected }) {
                toast.get().countDown()
            }
        }
        try {
            fixture.navigate("/settings")
            val label = context.getString(R.string.settings_arrival_switch)
            val toggle = compose.onNodeWithContentDescription(label, useUnmergedTree = true)
            toggle.performScrollTo().assertIsOff().assertIsNotEnabled()
            toggle.performTouchInput { click(center) }
            fixture.capture("oppo-gms-disabled-settings-toast")
            assertTrue("Settings switch must show the exact Toast", toast.get().await(5, TimeUnit.SECONDS))
            toggle.assertIsOff()
            assertFalse(store.enabled)
            assertFalse(store.enableRequested)
            fixture.capture("oppo-gms-disabled-settings")

            compose.onNode(hasText(context.getString(R.string.settings_arrival)) and hasClickAction())
                .performClick()
            compose.onNodeWithTag("arrival-settings-content").assertIsDisplayed()
            toast.set(CountDownLatch(1))
            val detailToggle = compose.onNodeWithContentDescription(label, useUnmergedTree = true)
            detailToggle.assertIsOff().assertIsNotEnabled()
            detailToggle.performTouchInput { click(center) }
            assertTrue("Detail switch must show the exact Toast", toast.get().await(5, TimeUnit.SECONDS))
            detailToggle.assertIsOff()
            assertFalse(store.enabled)
            assertFalse(store.enableRequested)
            compose.onNodeWithText(context.getString(R.string.arrival_permission_title)).assertDoesNotExist()
            fixture.capture("oppo-gms-disabled-arrival")
        } finally {
            instrumentation.uiAutomation.setOnAccessibilityEventListener(null)
            fixture.close()
            welcome.close()
            store.enabled = originalEnabled
            store.enableRequested = originalRequested
        }
    }
}
