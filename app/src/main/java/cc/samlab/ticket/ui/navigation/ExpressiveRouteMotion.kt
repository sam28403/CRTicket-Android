package cc.samlab.ticket.ui.navigation

import androidx.activity.BackEventCompat
import android.graphics.Path
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Mirrors AOSP DefaultCrossActivityBackAnimation, independently of NavHost's seek timeline.
 * Reference: frameworks/base, libs/WindowManager/Shell/src/com/android/wm/shell/back/.
 */
internal object ExpressiveRouteMotion {
    const val PreviewScale = 0.9f
    const val CommitDuration = 450
    private val gesture = PathInterpolator(0.1f, 0.1f, 0f, 1f)
    private val animator = AccelerateDecelerateInterpolator()
    private val emphasized = PathInterpolator(Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    })

    fun gestureProgress(progress: Float) = gesture.getInterpolation(progress.coerceIn(0f, 1f))

    fun preview(
        rawProgress: Float,
        edge: Int,
        deltaY: Float,
        width: Float,
        height: Float,
        density: Float,
        scrim: Float,
    ): Pair<RouteFrame, RouteFrame> {
        val progress = gestureProgress(rawProgress)
        val scale = 1f - (1f - PreviewScale) * progress
        val horizontalInset = width * (1f - scale) / 2f
        val verticalInset = height * (1f - scale) / 2f
        val yRatio = min(abs(deltaY) / (height / 2f), 1f)
        val yShift = max(verticalInset - 8f * density, 0f) *
            (1f - (1f - yRatio) * (1f - yRatio)) * if (deltaY < 0f) -1f else 1f
        val shift = if (edge == BackEventCompat.EDGE_LEFT) {
            (width * (1f - PreviewScale) / 2f - 8f * density) * progress
        } else 0f
        val closing = RouteFrame(horizontalInset + shift, verticalInset + yShift, scale)
        val entering = RouteFrame(horizontalInset - 96f * density, verticalInset + yShift, scale, dim = scrim)
        return closing to entering
    }

    fun commit(
        timeMillis: Float,
        closing: RouteFrame,
        entering: RouteFrame,
        density: Float,
        velocity: Float,
        rawProgress: Float,
        width: Float,
        height: Float,
    ): Pair<RouteFrame, RouteFrame> {
        // ValueAnimator's default interpolator runs before the Shell's emphasized curve.
        val fraction = animator.getInterpolation((timeMillis / CommitDuration).coerceIn(0f, 1f))
        val progress = emphasized.getInterpolation(fraction)
        val bounce = flingScale(timeMillis, velocity, rawProgress)
        val outgoing = closing.towards(RouteFrame(x = closing.x + 96f * density), progress)
            .copy(alpha = max(1f - fraction * 5f, 0f))
        val incoming = entering.towards(RouteFrame(), progress)
            .copy(dim = entering.dim * (1f - fraction))
        return outgoing.bounce(bounce, width, height) to incoming.bounce(bounce, width, height)
    }

    private fun flingScale(timeMillis: Float, velocity: Float, rawProgress: Float): Float {
        val startVelocity = (velocity * 0.2f)
            .coerceAtLeast(if (gesture.getInterpolation(rawProgress) < 0.1f) 1.2f else 0f)
            .coerceAtMost(10f)
        val omega = sqrt(200f)
        val damped = omega * sqrt(0.75f)
        val seconds = timeMillis / 1_000f
        return min(1f, 1f - startVelocity / damped * exp(-0.5f * omega * seconds) * sin(damped * seconds))
    }
}

internal data class RouteFrame(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val dim: Float = 0f,
) {
    fun towards(other: RouteFrame, progress: Float) = RouteFrame(
        x + (other.x - x) * progress,
        y + (other.y - y) * progress,
        scale + (other.scale - scale) * progress,
        alpha + (other.alpha - alpha) * progress,
        dim + (other.dim - dim) * progress,
    )

    fun bounce(factor: Float, width: Float, height: Float) = copy(
        x = x + width * scale * (1f - factor) / 2f,
        y = y + height * scale * (1f - factor) / 2f,
        scale = scale * factor,
    )
}
