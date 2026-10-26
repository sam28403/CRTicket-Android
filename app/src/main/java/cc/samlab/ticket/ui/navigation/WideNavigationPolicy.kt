package cc.samlab.ticket.ui.navigation

/** The menu stays visible, so changing features starts a new detail journey. */
internal object WideNavigationPolicy {
    private val children = setOf(
        "/railfan/quiz/report",
        "/price/list",
        "/about/report",
        "/about/credits",
        "/sale-time/reminders",
        "/settings/sale-reminders",
        "/settings/arrival",
        "/settings/permissions",
        "/settings/map",
        "/settings/language",
        "/history/editor",
        "/monitor/settings",
        "/railfan/speedometer/guide",
        "/railfan/speedometer/live",
        "/railfan/speedometer/track",
    )

    fun startsJourney(location: String): Boolean {
        val path = location.substringBefore('?')
        if (path in children || path.startsWith("/help/")) return false
        val argument = when (path) {
            "/train" -> "train"
            "/station" -> "station"
            "/railfan/lines" -> "line"
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
