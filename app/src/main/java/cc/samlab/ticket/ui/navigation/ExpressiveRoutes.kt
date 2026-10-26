package cc.samlab.ticket.ui.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry

internal object ExpressiveRoutes {
    val pages = setOf(
        "/price", "/price/list",
        "/", "/home", "/about", "/settings", "/settings/arrival", "/settings/permissions",
        "/settings/map", "/settings/language", "/settings/sale-reminders", "/search", "/sale-time/reminders",
        "/history", "/history/editor", "/list", "/station", "/sale-time", "/train", "/user",
        "/help", "/help/tickets", "/help/query",
        "/help/backup", "/help/arrival", "/help/monitor",
        "/help/sale", "/help/speed", "/help/vehicle",
        "/help/general",
        "/monitor", "/monitor/settings", "/about/report", "/about/credits",
        "/railfan/lines", "/railfan/vehicle", "/railfan/routing", "/railfan/visits", "/railfan/speedometer",
        "/railfan/speedometer/guide", "/railfan/speedometer/live", "/railfan/speedometer/track",
    )

    fun path(location: String): String = Uri.parse(location).path.orEmpty()

    fun isNative(location: String) = path(location) in pages

    fun addsDefaultDate(original: String, updated: String): Boolean {
        val before = Uri.parse(original)
        val after = Uri.parse(updated)
        if (before.path != after.path || after.queryParameterNames - before.queryParameterNames != setOf("date")) {
            return false
        }
        return before.queryParameterNames.all {
            before.getQueryParameters(it) == after.getQueryParameters(it)
        }
    }

    fun webLocation(location: String): String = when (path(location)) {
        "/price", "/price/list" -> "/home"
        "/help/tickets", "/help/query",
        "/help/backup", "/help/arrival", "/help/monitor",
        "/help/sale", "/help/speed", "/help/vehicle",
        "/help/general",
        "/help" -> "/home"
        "/sale-time", "/sale-time/reminders" -> "/home"
        "/railfan/lines", "/railfan/vehicle", "/railfan/routing", "/railfan/visits", "/railfan/speedometer" -> "/home"
        "/railfan/speedometer/guide", "/railfan/speedometer/live", "/railfan/speedometer/track" -> "/home"
        "/about/report", "/about/credits" -> "/about"
        "/settings/arrival", "/settings/permissions", "/settings/map", "/settings/language", "/settings/sale-reminders" -> "/settings"
        "/history/editor" -> "/history"
        else -> location
    }

    fun graphRoute(location: String): String {
        val page = path(location).takeIf { it in pages } ?: "/web"
        return "$page?location=${Uri.encode(location)}"
    }

    fun location(entry: NavBackStackEntry): String =
        entry.arguments?.getString("location") ?: "/home"
}
