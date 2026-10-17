package cc.samlab.ticket

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.statistics.StatisticsBodyLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsTouchTest {
    @Test fun smallTapJitterDoesNotStartOrSettleHeaderScrolling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val body = StatisticsBodyLayout(context)
            val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
            var scrolls = 0
            var ends = 0
            body.onScroll = { _, _ -> scrolls++ }
            body.onScrollEnd = { ends++ }
            fun send(action: Int, y: Float) {
                val event = MotionEvent.obtain(0, 10, action, 100f, y, 0)
                body.dispatchTouchEvent(event)
                event.recycle()
            }
            send(MotionEvent.ACTION_DOWN, 100f)
            send(MotionEvent.ACTION_MOVE, 100f + slop / 2)
            send(MotionEvent.ACTION_UP, 100f + slop / 2)
            assertEquals(0, scrolls)
            assertEquals(0, ends)
            send(MotionEvent.ACTION_DOWN, 100f)
            send(MotionEvent.ACTION_MOVE, 100f + slop * 2)
            send(MotionEvent.ACTION_UP, 100f + slop * 2)
            assertTrue(scrolls > 0)
            assertEquals(1, ends)
        }
    }
}
