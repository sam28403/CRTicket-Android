package cc.samlab.ticket

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import org.json.JSONObject

internal class FramePerformanceRecorder(private val window: Window) {
    private val thread = HandlerThread("frame-metrics").apply { start() }
    private val samples = mutableListOf<DoubleArray>()
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6
        val layout = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1e6
        val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION) / 1e6
        synchronized(samples) { samples.add(doubleArrayOf(total, layout, draw)) }
    }

    fun attach() = window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
    fun reset() = synchronized(samples) { samples.clear() }

    fun summary(): JSONObject {
        val values = synchronized(samples) { samples.toList() }
        val total = values.map { it[0] }.sorted()
        fun percentile(fraction: Double) = if (total.isEmpty()) 0.0
            else total[((total.size - 1) * fraction).toInt()]
        return JSONObject().apply {
            put("frames", total.size)
            put("over16ms", total.count { it > 16.67 })
            put("over32ms", total.count { it > 33.33 })
            put("p50ms", percentile(0.5))
            put("p95ms", percentile(0.95))
            put("maxMs", total.lastOrNull() ?: 0.0)
            put("maxLayoutMs", values.maxOfOrNull { it[1] } ?: 0.0)
            put("maxDrawMs", values.maxOfOrNull { it[2] } ?: 0.0)
        }
    }

    fun close() {
        window.removeOnFrameMetricsAvailableListener(listener)
        thread.quitSafely()
    }
}
