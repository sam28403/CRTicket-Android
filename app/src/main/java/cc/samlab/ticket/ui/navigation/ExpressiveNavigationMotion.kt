package cc.samlab.ticket.ui.navigation

import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Owns the clock: a committed gesture always gets a fresh system-length release phase. */
internal class ExpressiveNavigationMotion(
    private val nav: NavHostController,
    private val navigator: ComposeNavigator,
) {
    var scene by mutableStateOf<NavigationScene?>(null)
        private set
    var shown by mutableStateOf<NavBackStackEntry?>(null)
        private set
    var size = IntSize(1, 1)
    var density = 1f
    var scrim = 0.8f
    private var normalJob: Job? = null
    private var settling = false
    private val pendingCompletions = mutableSetOf<NavBackStackEntry>()

    private fun completeTransitions(vararg entries: NavBackStackEntry) {
        pendingCompletions.addAll(entries)
        val drawing = scene?.let { setOf(it.outgoing.id, it.incoming.id) }.orEmpty()
        pendingCompletions.filter { it.id !in drawing }.forEach { entry ->
            navigator.onTransitionComplete(entry)
            pendingCompletions.remove(entry)
        }
    }

    fun pendingScene(
        stack: List<NavBackStackEntry>,
        system: SystemRouteAnimations,
    ): NavigationScene? {
        val source = shown ?: return null
        val destination = stack.lastOrNull() ?: return null
        if (source.id == destination.id) return null
        val back = stack.none { it.id == source.id }
        val transition = RouteTransition(
            enter = if (back) system.closeEnter else system.openEnter,
            exit = if (back) system.closeExit else system.openExit,
        )
        return NavigationScene(
            source, destination, transition.closing, transition.entering, back,
            transition = transition,
        )
    }

    suspend fun navigate(stack: List<NavBackStackEntry>, system: SystemRouteAnimations) {
        val destination = stack.lastOrNull() ?: return
        val source = shown
        val next = pendingScene(stack, system)
        shown = destination
        if (source == null || source.id == destination.id) {
            navigator.onTransitionComplete(destination)
            return
        }
        normalJob = currentCoroutineContext()[Job]
        val transition = requireNotNull(next?.transition)
        val duration = transition.duration
        scene = next
        try {
            // Lay out the incoming page at its initial pose before starting its clock.
            withFrameNanos { }
            Animatable(0f).animateTo(duration.toFloat(), tween(duration, easing = LinearEasing)) {
                transition.timeMillis = value
            }
            scene = null
        } finally {
            pendingCompletions.addAll(listOf(source, destination))
            // An interrupted push can become the outgoing page of the next pop.
            // Retain its owners until that replacement animation has stopped drawing it.
            if (currentCoroutineContext().isActive) completeTransitions()
            normalJob = null
        }
    }

    suspend fun back(collect: suspend (suspend (BackEventCompat) -> Unit) -> Unit) =
        runBack(collect)

    private suspend fun runBack(collect: suspend (suspend (BackEventCompat) -> Unit) -> Unit) {
        if (settling) {
            collect { }
            return
        }
        val outgoing = nav.currentBackStackEntry ?: return
        val incoming = nav.previousBackStackEntry ?: return
        var firstY: Float? = null
        var lastEvent: BackEventCompat? = null
        var downTime = 0L
        val velocityTracker = VelocityTracker.obtain()
        try {
            collect { event ->
                if (firstY == null) {
                    normalJob?.cancel()
                    firstY = event.touchY
                    downTime = event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
                    navigator.prepareForTransition(outgoing)
                    navigator.prepareForTransition(incoming)
                }
                // Shell measures interpolated progress using event timestamps, not dispatch latency.
                val movement = MotionEvent.obtain(
                    downTime, event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, ExpressiveRouteMotion.gestureProgress(event.progress) * 100f,
                    0f, 0,
                )
                velocityTracker.addMovement(movement)
                movement.recycle()
                lastEvent = event
                val (closing, entering) = ExpressiveRouteMotion.preview(
                    event.progress, event.swipeEdge, event.touchY - requireNotNull(firstY),
                    size.width.toFloat(), size.height.toFloat(), density, scrim,
                )
                scene = NavigationScene(outgoing, incoming, closing, entering, back = true, predictive = true)
            }
            val preview = scene?.takeIf { it.predictive }
            if (preview != null) {
                settling = true
                velocityTracker.computeCurrentVelocity(1_000)
                val velocity = velocityTracker.xVelocity / 100f
                Animatable(0f).animateTo(
                    ExpressiveRouteMotion.CommitDuration.toFloat(),
                    tween(ExpressiveRouteMotion.CommitDuration, easing = LinearEasing),
                ) {
                    val (closing, entering) = ExpressiveRouteMotion.commit(
                        value, preview.closing, preview.entering, density, velocity,
                        lastEvent?.progress ?: 0f, size.width.toFloat(), size.height.toFloat(),
                    )
                    scene = preview.copy(closing = closing, entering = entering)
                }
                shown = incoming
                scene = null
            }
            nav.popBackStack()
        } catch (cancelled: CancellationException) {
            val preview = scene?.takeIf { it.predictive }
            if (preview != null) withContext(NonCancellable) {
                settling = true
                // Same critically damped spring as framework BackProgressAnimator.
                val event = lastEvent
                Animatable(event?.progress ?: 0f).animateTo(
                    0f, spring(dampingRatio = 1f, stiffness = 1_500f, visibilityThreshold = 0.0001f),
                ) {
                    val (closing, entering) = ExpressiveRouteMotion.preview(
                        value, event?.swipeEdge ?: BackEventCompat.EDGE_LEFT,
                        (event?.touchY ?: 0f) - (firstY ?: 0f),
                        size.width.toFloat(), size.height.toFloat(), density, scrim,
                    )
                    scene = preview.copy(
                        closing = closing, entering = entering,
                    )
                }
                scene = null
            }
        } finally {
            velocityTracker.recycle()
            settling = false
            // A button back starts an ordinary pop; that animation owns completion.
            if (firstY != null) withContext(NonCancellable + Dispatchers.Main.immediate) {
                completeTransitions(outgoing, incoming)
            }
        }
    }
}
