package cc.samlab.ticket.ui.navigation

import android.content.Context
import android.graphics.Matrix
import android.view.animation.AnimationUtils
import android.view.animation.Transformation

/** Samples the device's framework animations instead of approximating their timing curve.
 * Compose still owns the destinations, saved state, and the single native back stack.
 */
internal class SystemRouteAnimations(context: Context, width: Int, height: Int) {
    private val density = context.resources.displayMetrics.density
    val openEnter = sample(context, "activity_open_enter", width, height, true, true)
    val openExit = sample(context, "activity_open_exit", width, height, false, true)
    val closeEnter = sample(context, "activity_close_enter", width, height, true, false)
    val closeExit = sample(context, "activity_close_exit", width, height, false, false)

    private fun sample(
        context: Context,
        name: String,
        width: Int,
        height: Int,
        entering: Boolean,
        forward: Boolean,
    ): Frames {
        val resource = context.resources.getIdentifier(name, "anim", "android")
        val animation = if (resource != 0) {
            runCatching { AnimationUtils.loadAnimation(context, resource) }.getOrNull()
        } else null
        if (animation == null) return fallback(entering, forward)

        animation.initialize(width, height, width, height)
        animation.startTime = 0L
        val duration = animation.computeDurationHint().toInt().coerceIn(1, 2_000)
        val transformation = Transformation()
        val matrix = FloatArray(9)
        val times = (0 until duration).toList() + duration
        return Frames(duration, times.map { time ->
            transformation.clear()
            animation.getTransformation(time.toLong(), transformation)
            transformation.matrix.getValues(matrix)
            Frame(
                time,
                RouteFrame(
                    x = matrix[Matrix.MTRANS_X], y = matrix[Matrix.MTRANS_Y],
                    scale = matrix[Matrix.MSCALE_X], alpha = transformation.alpha,
                ),
            )
        })
    }

    // Some OEM frameworks omit the named AOSP resources. Keep the same horizontal motion.
    private fun fallback(entering: Boolean, forward: Boolean): Frames {
        val distance = 96 * density
        val direction = if (forward) 1 else -1
        return Frames(450, listOf(
            Frame(
                0, RouteFrame(
                    alpha = if (entering && forward) 0f else 1f,
                    x = if (entering) direction * distance else 0f,
                ),
            ),
            Frame(
                450, RouteFrame(
                    alpha = if (!entering && !forward) 0f else 1f,
                    x = if (entering) 0f else -direction * distance,
                ),
            ),
        ))
    }

    internal data class Frame(val time: Int, val value: RouteFrame)

    internal data class Frames(val duration: Int, val frames: List<Frame>) {
        fun at(timeMillis: Float): RouteFrame {
            val time = timeMillis.coerceIn(0f, duration.toFloat())
            val before = frames.last { it.time <= time }
            val after = frames.firstOrNull { it.time > time } ?: return before.value
            return before.value.towards(after.value, (time - before.time) / (after.time - before.time))
        }
    }
}
