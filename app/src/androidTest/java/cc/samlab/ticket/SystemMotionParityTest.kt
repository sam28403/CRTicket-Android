package cc.samlab.ticket

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.animation.AnimationUtils
import android.view.animation.PathInterpolator
import android.view.animation.Transformation
import androidx.activity.BackEventCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.ui.navigation.ExpressiveRouteMotion
import cc.samlab.ticket.ui.navigation.RouteFrame
import cc.samlab.ticket.ui.navigation.SystemRouteAnimations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemMotionParityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun normalMotionMatchesDeviceFrameworkAtEveryMillisecond() {
        val sampled = SystemRouteAnimations(context, 1_344, 2_992)
        mapOf(
            "activity_open_enter" to sampled.openEnter,
            "activity_open_exit" to sampled.openExit,
            "activity_close_enter" to sampled.closeEnter,
            "activity_close_exit" to sampled.closeExit,
        ).forEach { (name, frames) ->
            val resource = context.resources.getIdentifier(name, "anim", "android")
            assertTrue("Missing Pixel framework animation: $name", resource != 0)
            val original = AnimationUtils.loadAnimation(context, resource)
            original.initialize(1_344, 2_992, 1_344, 2_992)
            original.startTime = 0
            assertEquals(name, original.computeDurationHint().toInt(), frames.duration)
            val transformation = Transformation()
            val matrix = FloatArray(9)
            for (time in 0..frames.duration) {
                transformation.clear()
                original.getTransformation(time.toLong(), transformation)
                transformation.matrix.getValues(matrix)
                val actual = frames.at(time.toFloat())
                assertEquals("$name x at $time", matrix[Matrix.MTRANS_X], actual.x, 0.001f)
                assertEquals("$name scale at $time", matrix[Matrix.MSCALE_X], actual.scale, 0.0001f)
                assertEquals("$name alpha at $time", transformation.alpha, actual.alpha, 0.0001f)
            }
        }
    }

    @Test fun previewUsesSystemDecelerationRatherThanLinearProgress() {
        val original = PathInterpolator(0.1f, 0.1f, 0f, 1f)
        listOf(0f, 0.1f, 0.25f, 0.5f, 0.8f, 1f).forEach { progress ->
            val (closing, entering) = ExpressiveRouteMotion.preview(
                progress, BackEventCompat.EDGE_LEFT, 0f, 1_000f, 2_000f, 1f, 0.8f,
            )
            val expectedProgress = original.getInterpolation(progress)
            assertEquals(1f - 0.1f * expectedProgress, closing.scale, 0.0001f)
            assertEquals(closing.scale, entering.scale, 0.0001f)
            assertEquals(50f * expectedProgress + 42f * expectedProgress, closing.x, 0.001f)
        }
    }

    @Test fun releaseMatchesSystemAnimatorCurveAndDoesNotShortenAfterALongerGesture() {
        val resource = context.resources.getIdentifier("fast_out_extra_slow_in", "interpolator", "android")
        assertTrue(resource != 0)
        val emphasized = AnimationUtils.loadInterpolator(context, resource)
        val animator = ValueAnimator.ofFloat(1f, 0f).interpolator
        assertEquals(450, ExpressiveRouteMotion.CommitDuration)
        listOf(0.2f, 0.8f).forEach { gesture ->
            val (closing, entering) = ExpressiveRouteMotion.preview(
                gesture, BackEventCompat.EDGE_LEFT, 0f, 1_000f, 2_000f, 1f, 0.8f,
            )
            listOf(0f, 45f, 90f, 135f, 225f, 450f).forEach { time ->
                val fraction = animator.getInterpolation(time / 450f)
                val progress = emphasized.getInterpolation(fraction)
                val (actualClosing, actualEntering) = ExpressiveRouteMotion.commit(
                    time, closing, entering, 1f, 0f, gesture, 1_000f, 2_000f,
                )
                val expected = entering.towards(RouteFrame(), progress)
                assertEquals("Incoming x at $time", expected.x, actualEntering.x, 0.001f)
                assertEquals("Incoming scale at $time", expected.scale, actualEntering.scale, 0.0001f)
                assertEquals("Closing alpha at $time", (1f - fraction * 5f).coerceAtLeast(0f),
                    actualClosing.alpha, 0.0001f)
            }
        }
    }
}
