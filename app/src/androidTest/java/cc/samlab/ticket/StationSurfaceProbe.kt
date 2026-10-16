package cc.samlab.ticket

import android.view.Choreographer
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry

/** Observe actual display frames, including frames before the router finishes going back. */
internal class StationSurfaceProbe(private val view: View) : Choreographer.FrameCallback {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Volatile var frames = 0
        private set
    @Volatile var minimumAlpha = 1f
        private set
    @Volatile var hiddenFrames = 0
        private set
    private var running = false

    fun start() = instrumentation.runOnMainSync {
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        frames++
        minimumAlpha = minOf(minimumAlpha, view.alpha)
        if (!view.isShown) hiddenFrames++
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() = instrumentation.runOnMainSync {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }
}
