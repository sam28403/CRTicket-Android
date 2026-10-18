package cc.samlab.ticket.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry

internal data class NavigationScene(
    val outgoing: NavBackStackEntry,
    val incoming: NavBackStackEntry,
    val closing: RouteFrame,
    val entering: RouteFrame,
    val back: Boolean,
    val predictive: Boolean = false,
    val transition: RouteTransition? = null,
) {
    fun frame(incoming: Boolean): RouteFrame = if (incoming) {
        transition?.entering ?: entering
    } else {
        transition?.closing ?: closing
    }
}

/** Read the clock in the graphics layer, so animation frames do not recompose pages. */
internal class RouteTransition(
    private val enter: SystemRouteAnimations.Frames,
    private val exit: SystemRouteAnimations.Frames,
) {
    var timeMillis by mutableFloatStateOf(0f)
    val duration = maxOf(enter.duration, exit.duration)
    val entering get() = enter.at(timeMillis)
    val closing get() = exit.at(timeMillis)
}
