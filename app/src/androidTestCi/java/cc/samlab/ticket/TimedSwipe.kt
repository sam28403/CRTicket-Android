package cc.samlab.ticket

import android.app.Instrumentation
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

internal fun injectTimedSwipe(
    instrumentation: Instrumentation,
    x: Float,
    startY: Float,
    endY: Float,
) {
    val down = SystemClock.uptimeMillis()
    fun send(action: Int, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            check(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }
    send(MotionEvent.ACTION_DOWN, startY)
    for (step in 1..24) {
        Thread.sleep(16)
        send(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 24)
    }
    send(MotionEvent.ACTION_UP, endY)
}
