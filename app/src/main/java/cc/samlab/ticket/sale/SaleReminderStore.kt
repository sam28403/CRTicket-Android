package cc.samlab.ticket.sale

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import cc.samlab.ticket.ui.sale.saleZone

internal class SaleReminderStore(context: Context) {
    val preferences = context.getSharedPreferences("sale-reminders", Context.MODE_PRIVATE)
    private val app = context.applicationContext

    fun read(now: Long = System.currentTimeMillis()): List<SaleReminder> = runCatching {
        resetIfExpired(now)
        val values = JSONArray(preferences.getString("items", "[]"))
        List(values.length()) { index ->
            val row = values.getJSONObject(index)
            SaleReminder(row.getString("station"), row.getLong("saleAt"), row.optString("bureau"))
        }.filter { isTodayReminder(it, Instant.ofEpochMilli(now)) && !wasDelivered(it.saleAt) }
            .distinctBy { it.id }.sortedWith(compareBy<SaleReminder> { it.saleAt }.thenBy { it.station })
    }.getOrDefault(emptyList())

    fun add(row: SaleReminder): Boolean {
        if (!isTodayReminder(row, Instant.now()) || wasDelivered(row.saleAt) ||
            !SaleReminderAlarms.allowed(app)) return false
        val original = read()
        val rows = (original + row).distinctBy { it.id }
        write(rows)
        val scheduled = SaleReminderAlarms.reschedule(app, rows)
        if (!scheduled) write(original)
        return scheduled
    }

    fun remove(id: String) {
        val rows = read().filterNot { it.id == id }
        write(rows)
        SaleReminderAlarms.reschedule(app, rows)
    }

    fun wasDelivered(saleAt: Long): Boolean =
        saleAt.toString() in preferences.getStringSet("delivered", emptySet()).orEmpty()

    fun delivered(saleAt: Long) {
        val cutoff = System.currentTimeMillis() - 2 * 86_400_000L
        val sent = preferences.getStringSet("delivered", emptySet()).orEmpty()
            .filter { (it.toLongOrNull() ?: 0L) > cutoff }.toMutableSet()
        sent += saleAt.toString()
        preferences.edit().putStringSet("delivered", sent).commit()
        write(read().filterNot { it.saleAt == saleAt })
    }

    fun clearAll() {
        preferences.edit().remove("items").remove("delivered")
            .putString("day", Instant.now().atZone(saleZone).toLocalDate().toString()).commit()
        SaleReminderAlarms.cancelAll(app)
    }

    private fun resetIfExpired(now: Long) {
        val local = Instant.ofEpochMilli(now).atZone(saleZone)
        val day = local.toLocalDate().toString()
        val saved = preferences.getString("day", "")
        if (saved.isNullOrEmpty()) preferences.edit().putString("day", day).commit()
        val expired = saved?.isNotBlank() == true && saved != day
        if (expired || (!local.toLocalTime().isBefore(LocalTime.of(18, 0)) &&
            (preferences.contains("items") || preferences.contains("delivered")))) clearAll()
    }

    private fun write(rows: List<SaleReminder>) {
        val json = JSONArray().apply {
            rows.forEach { row -> put(JSONObject().put("station", row.station)
                .put("saleAt", row.saleAt).put("bureau", row.bureau)) }
        }
        preferences.edit().putString("items", json.toString()).commit()
    }
}
