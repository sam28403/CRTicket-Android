package cc.samlab.ticket.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TicketMonitorStore {
    private fun prefs(context: Context) =
        context.getSharedPreferences("ticket-monitor", Context.MODE_PRIVATE)

    fun read(context: Context): JSONObject =
        runCatching { JSONObject(prefs(context).getString("state", "{}")!!) }
            .getOrDefault(JSONObject())

    fun save(context: Context, state: JSONObject) {
        // Commit the user's intent before returning to the page or leaving the activity.
        prefs(context).edit().putString("state", state.toString()).commit()
    }

    fun stop(context: Context) {
        save(context, read(context).put("active", false).put("loading", false).put("nextAt", ""))
    }

    fun status(context: Context): JSONObject = read(context).apply {
        put("success", true)
        if (!has("active")) put("active", false)
        if (!has("config")) put("config", JSONObject.NULL)
        if (!has("trains")) put("trains", JSONArray())
        if (!has("events")) put("events", JSONArray())
        if (!has("queriedAt")) put("queriedAt", "")
        if (!has("nextAt")) put("nextAt", "")
        if (!has("error")) put("error", "")
        if (!has("loading")) put("loading", false)
    }
}
