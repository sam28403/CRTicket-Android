package cc.samlab.ticket

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListScrollTest {
    @Test fun stationVirtualTableKeepsScrollingAfterFingerRelease() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
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
            fixture.await {
                fixture.evaluate("Boolean(window.crticketBack)") == "true"
            }
            // Only mock railway data for this activity. No saved records are changed.
            fixture.evaluate("""
                const originalRequest = CRTicketAndroid.request;
                CRTicketAndroid.request = (id, method, rawUrl, body) => {
                    const url = new URL(rawUrl);
                    if (url.pathname !== '/otn/czxx/query') {
                        return originalRequest(id, method, rawUrl, body);
                    }
                    const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
                    const rows = Array.from({length: 1000}, (_, index) => ({
                        train_no: 'scroll-fixture-' + index,
                        station_train_code: 'G' + (index + 1),
                        station_name: '北京南', start_station_name: '北京南',
                        end_station_name: '上海虹桥', start_time: '12:05',
                        start_train_date: date, checkin: 'A1'
                    }));
                    queueMicrotask(() => window.crticketApiResult(id, {
                        status: 200, contentType: 'application/json',
                        body: JSON.stringify({status: true, data: {flag: true, data: rows}})
                    }));
                };
                location.hash = '/station?station=' + encodeURIComponent('北京南');
            """.trimIndent())
            val selector = ".native-table-scroll .el-table-v2__body > div:first-child"
            fixture.await {
                fixture.evaluate("Boolean(document.querySelector('$selector'))") == "true"
            }
            fixture.evaluate("""
                document.querySelector('.board-actions .el-switch').click();
                window.scrollSurface = document.querySelector('$selector');
                const bounds = scrollSurface.getBoundingClientRect();
                window.scrollTo(0, bounds.top + window.scrollY - 100);
                scrollSurface.scrollTop = 1000;
                window.scrollTestSentinel = true;
            """.trimIndent())
            Thread.sleep(150)
            val x = fixture.evaluate(
                "scrollSurface.getBoundingClientRect().left + scrollSurface.clientWidth / 2"
            ).toFloat()
            val startY = fixture.evaluate(
                "Math.min(scrollSurface.getBoundingClientRect().bottom, innerHeight) - 40"
            ).toFloat()
            val initialTop = fixture.evaluate("scrollSurface.scrollTop").toDouble()
            fixture.touch(MotionEvent.ACTION_DOWN, x, startY)
            repeat(5) { index ->
                Thread.sleep(10)
                fixture.touch(MotionEvent.ACTION_MOVE, x, startY - 160 * (index + 1) / 5)
            }
            fixture.touch(MotionEvent.ACTION_UP, x, startY - 160)
            Thread.sleep(350)
            val afterRelease = fixture.evaluate("scrollSurface.scrollTop").toDouble()
            assertTrue("Fling did not travel beyond finger distance: $initialTop -> $afterRelease",
                afterRelease - initialTop > 200)
            assertEquals("false", fixture.evaluate(
                "document.querySelector('[role=switch]').getAttribute('aria-checked') === 'true'"
            ))
            assertTrue(fixture.evaluate(
                "document.querySelectorAll('.el-table-v2__row').length"
            ).toInt() < 80)
            assertEquals("true", fixture.evaluate("window.scrollTestSentinel === true"))
        } finally {
            fixture.close()
        }
    }
}
