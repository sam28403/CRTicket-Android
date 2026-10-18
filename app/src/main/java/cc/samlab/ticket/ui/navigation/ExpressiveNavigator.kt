package cc.samlab.ticket.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavHostController

/** One native back stack, with the web router mirroring only committed locations. */
internal class ExpressiveNavigator {
    private var controller: NavHostController? = null
    private var listener: NavController.OnDestinationChangedListener? = null
    private data class Request(
        val location: String,
        val fromWeb: Boolean,
        val replace: Boolean = false,
        val reset: Boolean = false,
    )
    private var pending: Request? = null
    private var mirror = true

    val currentLocation: String?
        get() = controller?.currentBackStackEntry?.let(ExpressiveRoutes::location)

    fun attach(nav: NavHostController, onDestination: (String, Boolean, Boolean) -> Unit) {
        detach()
        controller = nav
        val initial = pending
        pending = null
        if (initial != null) {
            navigate(initial.location, initial.fromWeb, initial.replace, initial.reset)
        }
        val visited = mutableSetOf<String>()
        runCatching { nav.getBackStackEntry(nav.graph.startDestinationId).id }.getOrNull()?.let(visited::add)
        var initialNotification = true
        val observer = NavController.OnDestinationChangedListener { _, _, _ ->
            val entry = nav.currentBackStackEntry ?: return@OnDestinationChangedListener
            val back = entry.id in visited
            visited.add(entry.id)
            nav.previousBackStackEntry?.id?.let(visited::add)
            val shouldMirror = if (initialNotification) initial?.let { !it.fromWeb } ?: false else mirror
            initialNotification = false
            onDestination(ExpressiveRoutes.location(entry), back, shouldMirror)
        }
        listener = observer
        nav.addOnDestinationChangedListener(observer)
    }

    fun navigate(
        location: String,
        fromWeb: Boolean = false,
        replace: Boolean = false,
        reset: Boolean = false,
    ): Boolean {
        val target = location.ifEmpty { "/" }
        val nav = controller
        if (nav == null) {
            pending = Request(target, fromWeb, replace, reset)
            return true
        }
        if (!reset && (currentLocation == target ||
            (fromWeb && currentLocation?.let(ExpressiveRoutes::webLocation) == target))) return false
        mirror = !fromWeb
        try {
            val previous = nav.previousBackStackEntry?.let(ExpressiveRoutes::location)
            if (reset) {
                // Every notification starts a fresh journey rooted at home, even
                // when the same train is already the current destination.
                nav.navigate(ExpressiveRoutes.graphRoute(target)) {
                    popUpTo(nav.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            } else if (target == "/home") {
                nav.popBackStack(nav.graph.startDestinationId, false)
            } else if (replace ||
                (fromWeb && currentLocation?.let { ExpressiveRoutes.addsDefaultDate(it, target) } == true)) {
                nav.navigate(ExpressiveRoutes.graphRoute(target)) {
                    popUpTo(nav.currentDestination!!.id) { inclusive = true }
                }
            } else if (previous == target ||
                (fromWeb && previous?.let(ExpressiveRoutes::webLocation) == target)) nav.popBackStack()
            else if ((target == "/settings" && currentLocation?.startsWith("/settings/") == true) ||
                (target == "/history" && currentLocation == "/history/editor")) {
                if (!nav.popBackStack(ExpressiveRoutes.graphRoute(target), false)) {
                    nav.navigate(ExpressiveRoutes.graphRoute(target))
                }
            }
            else nav.navigate(ExpressiveRoutes.graphRoute(target))
        } finally {
            mirror = true
        }
        return true
    }

    fun pop(): Boolean = controller?.let { nav ->
        nav.previousBackStackEntry != null && nav.popBackStack()
    } ?: false

    fun detach() {
        listener?.let { controller?.removeOnDestinationChangedListener(it) }
        listener = null
        controller = null
    }
}
