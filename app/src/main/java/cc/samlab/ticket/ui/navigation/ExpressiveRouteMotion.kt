package cc.samlab.ticket.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing

/** Essentials-inspired gentle scale/fade, shared by every native destination.
 * Reference: sameerasw/essentials, MainActivity.kt, commit 937ce471 (back progress and settle).
 */
internal object ExpressiveRouteMotion {
    const val EnterDuration = 400
    const val ReturnDuration = 400
    const val FadeDuration = 300
    const val BackgroundScale = 0.95f
    const val ReturnScale = 0.95f
    val Easing = FastOutSlowInEasing
}
