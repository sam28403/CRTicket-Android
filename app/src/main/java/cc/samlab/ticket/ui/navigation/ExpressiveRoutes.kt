package cc.samlab.ticket.ui.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry

internal object ExpressiveRoutes {
    val pages = setOf(
        "/", "/home", "/about", "/settings", "/settings/arrival", "/settings/permissions",
        "/settings/language",
        "/history", "/history/editor", "/list", "/station", "/train", "/user",
        "/monitor", "/monitor/settings", "/about/report",
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
        "/about/report" -> "/about"
        "/settings/arrival", "/settings/permissions", "/settings/language" -> "/settings"
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
