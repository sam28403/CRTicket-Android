package cc.samlab.ticket.ui.navigation

/** The menu stays visible, so changing features starts a new detail journey. */
internal object WideNavigationPolicy {
    private val children = setOf(
        "/about/report",
        "/settings/arrival",
        "/settings/permissions",
        "/history/editor",
        "/monitor/settings",
    )

    fun startsJourney(location: String): Boolean {
        val path = location.substringBefore('?')
        if (path in children) return false
        val argument = when (path) {
            "/train" -> "train"
            "/station" -> "station"
            else -> return true
        }
        // Filled query links preserve the page from which the user opened them.
        return location.substringAfter('?', "").split('&').none {
            it.startsWith("$argument=") && it.substringAfter('=').isNotBlank()
        }
    }

    fun canPop(largeScreen: Boolean, previous: String?): Boolean =
        previous != null && (!largeScreen || previous != "/home")
}
