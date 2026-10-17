package cc.samlab.ticket.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavHostController

/** One native back stack, with the web router mirroring only committed locations. */
internal class ExpressiveNavigator {
    private var controller: NavHostController? = null
    private var listener: NavController.OnDestinationChangedListener? = null
    private var pending: Pair<String, Boolean>? = null
    private var mirror = true

    val currentLocation: String?
        get() = controller?.currentBackStackEntry?.let(ExpressiveRoutes::location)

    fun attach(nav: NavHostController, onDestination: (String, Boolean, Boolean) -> Unit) {
        detach()
        controller = nav
        val initial = pending
        pending = null
        if (initial != null && currentLocation != initial.first) {
            nav.navigate(ExpressiveRoutes.graphRoute(initial.first))
        }
        val visited = mutableSetOf<String>()
        runCatching { nav.getBackStackEntry(nav.graph.startDestinationId).id }.getOrNull()?.let(visited::add)
        var initialNotification = true
        val observer = NavController.OnDestinationChangedListener { _, _, _ ->
            val entry = nav.currentBackStackEntry ?: return@OnDestinationChangedListener
            val back = entry.id in visited
            visited.add(entry.id)
            nav.previousBackStackEntry?.id?.let(visited::add)
            val shouldMirror = if (initialNotification) initial?.second ?: false else mirror
            initialNotification = false
            onDestination(ExpressiveRoutes.location(entry), back, shouldMirror)
        }
        listener = observer
        nav.addOnDestinationChangedListener(observer)
    }

    fun navigate(location: String, fromWeb: Boolean = false): Boolean {
        val target = location.ifEmpty { "/" }
        val nav = controller
        if (nav == null) {
            pending = target to !fromWeb
            return true
        }
        if (currentLocation == target ||
            (fromWeb && currentLocation?.let(ExpressiveRoutes::webLocation) == target)) return false
        mirror = !fromWeb
        try {
            val previous = nav.previousBackStackEntry?.let(ExpressiveRoutes::location)
            if (target == "/home") {
                nav.popBackStack(nav.graph.startDestinationId, false)
            } else if (fromWeb && currentLocation?.let { ExpressiveRoutes.addsDefaultDate(it, target) } == true) {
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
