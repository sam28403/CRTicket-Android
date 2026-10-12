package cc.samlab.ticket

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.tracking.TrainTrackingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PullRefreshTest {
    @Test fun scrollToTopThenShortPullRefreshesOnRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalTracking = TrainTrackingStore.read(context)
        val originalSession = TrainTrackingStore.session(context)
        val fixture = WebViewTouchFixture(instrumentation)
        try {
            fixture.await {
                var loaded = false
                instrumentation.runOnMainSync {
                    loaded = fixture.activity.webView.progress == 100 &&
                        fixture.activity.webView.url?.startsWith(MainActivity.ORIGIN) == true
                }
                loaded
            }
            fixture.await { fixture.evaluate(
                "Boolean(window.crticketBack && document.readyState === 'complete')") == "true" }
            fixture.evaluate("location.hash = '/train'")
            fixture.await { fixture.evaluate("document.querySelector('h1')?.textContent") == "\"车次查询\"" }
            val recordScript = """JSON.stringify([
                localStorage.getItem('crticket-device-tickets-v1'),
                localStorage.getItem('crticket-device-owner-v1')])""".trimIndent()
            val records = fixture.evaluate(recordScript)
            assertEquals("true", fixture.evaluate(
                "typeof CRTicketAndroid.setPullRefreshProgress === 'function' && " +
                    "typeof CRTicketAndroid.refreshPage === 'function'"))
            // Start on the title link: a pull refreshes, but must not activate that link.
            val x = 130f
            fixture.evaluate("""
                window.refreshTestSentinel = true;
                const filler = document.createElement('div');
                filler.style.height = '1200px';
                document.body.append(filler);
                window.scrollTo(0, 160);
            """.trimIndent())
            // Use the page margin while scrolling up, clear of the query inputs.
            fixture.pull(370f, 240f)
            assertEquals("true", fixture.evaluate("window.refreshTestSentinel === true"))
            fixture.await {
                fixture.evaluate("document.scrollingElement.scrollTop <= 2") == "true"
            }

            fixture.pull(x, 20f, release = false)
            fixture.await {
                var visible = false
                instrumentation.runOnMainSync {
                    visible = fixture.activity.findViewById<View>(android.R.id.content)
                        .findViewWithTag<View>("expressive-pull-refresh")?.visibility == View.VISIBLE
                }
                visible
            }
            assertEquals("true", fixture.evaluate("window.refreshTestSentinel === true"))
            val output = File(context.getExternalFilesDir(null), "pull-refresh-scenes").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(output, "expressive-pull.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            fixture.touch(MotionEvent.ACTION_UP, x, 45f)
            fixture.await {
                var finished = false
                instrumentation.runOnMainSync {
                    finished = fixture.activity.findViewById<View>(android.R.id.content)
                        .findViewWithTag<View>("expressive-pull-refresh")?.visibility != View.VISIBLE
                }
                finished
            }
            fixture.await { fixture.evaluate(
                "Boolean(!window.refreshTestSentinel && window.crticketBack && " +
                    "document.readyState === 'complete')") == "true" }
            assertEquals("\"#/train\"", fixture.evaluate("location.hash"))
            assertEquals(records, fixture.evaluate(recordScript))
            assertEquals(originalTracking, TrainTrackingStore.read(context))
            assertEquals(originalSession, TrainTrackingStore.session(context))
            instrumentation.runOnMainSync {
                val overlay = fixture.activity.findViewById<View>(android.R.id.content)
                    .findViewWithTag<View>("expressive-pull-refresh")
                assertNotNull(overlay)
                assertTrue(overlay.visibility != View.VISIBLE)
            }
        } finally {
            fixture.close()
        }
    }
}
