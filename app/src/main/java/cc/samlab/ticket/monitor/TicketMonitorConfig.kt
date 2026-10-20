package cc.samlab.ticket.monitor

import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.appString
import cc.samlab.ticket.railway.TrainNumberRules
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

data class TicketMonitorConfig(
    val from: String,
    val to: String,
    val date: String,
    val fromName: String,
    val toName: String,
    val interval: Long,
    val filters: JSONObject,
) {
    fun expired(): Boolean = date < LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()

    fun json(): JSONObject = JSONObject()
        .put("from", from).put("to", to).put("date", date)
        .put("fromName", fromName).put("toName", toName)
        .put("interval", interval).put("filters", filters)

    companion object {
        fun parse(payload: String): TicketMonitorConfig {
            val json = JSONObject(payload)
            val from = json.getString("from")
            val to = json.getString("to")
            require(Regex("[A-Z]{3}").matches(from) && Regex("[A-Z]{3}").matches(to)) {
                appString(R.string.monitor_error_stations)
            }
            val date = json.getString("date")
            val parsed = LocalDate.parse(date)
            val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            require(parsed.toString() == date && !parsed.isBefore(today) &&
                !parsed.isAfter(today.plusDays(14))) { appString(R.string.monitor_error_date_range) }
            val filters = json.optJSONObject("filters") ?: JSONObject()
            val trains = filters.optJSONArray("trainFilters")
            for (index in 0 until (trains?.length() ?: 0)) {
                val error = TrainNumberRules.error(trains!!.optString(index).trim().uppercase())
                require(error.isEmpty()) { error }
            }
            return TicketMonitorConfig(
                from, to, date,
                json.optString("fromName", from).take(50),
                json.optString("toName", to).take(50),
                json.optLong("interval", 60).coerceIn(10, 3600),
                filters,
            )
        }
    }
}
