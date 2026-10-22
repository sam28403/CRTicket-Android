package cc.samlab.ticket.backup

import android.content.Context
import android.content.SharedPreferences
import cc.samlab.ticket.speedometer.SpeedometerStore
import cc.samlab.ticket.arrival.ArrivalStore
import cc.samlab.ticket.arrival.ArrivalHistory
import cc.samlab.ticket.arrival.StationArrivalMonitor
import cc.samlab.ticket.i18n.AppLanguages
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.monitor.TicketMonitorStore
import cc.samlab.ticket.ui.notice.IMPORTANT_NOTICE_SUPPRESSED
import org.json.JSONObject

/** Portable preferences and records; permissions, caches and live sessions stay on the device. */
internal class BackupPreferences(private val context: Context) {
    private val android = context.getSharedPreferences("android", Context.MODE_PRIVATE)
    private val arrival = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
    private val monitor = context.getSharedPreferences("ticket-monitor", Context.MODE_PRIVATE)
    private var pending: JSONObject? = null
    private val railfan = BackupRailfanData(context)
    private val generator = context.getSharedPreferences("ticket-generator", Context.MODE_PRIVATE)

    fun snapshot(): JSONObject = railfan.snapshot(JSONObject()
        .put("speedRecordings", SpeedometerStore.backup(context))
        .put("generatorDraft", generator.getString("draft", null)?.let(::JSONObject) ?: JSONObject.NULL)
        .put("language", AppLanguages.selectedTag())
        .put("arrivalEnabled", ArrivalStore(context).enabled)
        .put("importantNoticeSuppressed", android.getBoolean(IMPORTANT_NOTICE_SUPPRESSED, false))
        .put("monitorConfig", TicketMonitorStore.read(context).optJSONObject("config") ?: JSONObject.NULL))

    fun validate(value: JSONObject) {
        railfan.validate(value)
        if (value.has("speedRecordings")) validateSpeedometerBackup(value.getJSONArray("speedRecordings"))
        if (value.has("generatorDraft") && !value.isNull("generatorDraft")) {
            validateGeneratorDraft(value.getJSONObject("generatorDraft"))
        }
        if (value.has("language")) {
            val language = value.get("language")
            require(language is String && (language.isEmpty() ||
                AppLanguages.supported(context).any { it.tag == language })) { "Invalid backup language" }
        }
        for (key in listOf("arrivalEnabled", "importantNoticeSuppressed")) {
            require(!value.has(key) || value.get(key) is Boolean) { "Invalid backup preference: $key" }
        }
        if (value.has("monitorConfig") && !value.isNull("monitorConfig")) {
            validateBackupMonitorConfig(value.getJSONObject("monitorConfig"))
        }
    }

    fun restore(value: JSONObject) = synchronized(ArrivalHistory.lock) {
        validate(value)
        val changes = mutableMapOf<SharedPreferences, Map<String, Any?>>()
        if (value.has("importantNoticeSuppressed")) {
            changes[android] = mapOf(IMPORTANT_NOTICE_SUPPRESSED to value.getBoolean("importantNoticeSuppressed"))
        }
        if (value.has("arrivalEnabled")) {
            changes[arrival] = mapOf("enabled" to value.getBoolean("arrivalEnabled"))
        }
        if (value.has("monitorConfig")) {
            changes[monitor] = mapOf("state" to JSONObject()
                .put("active", false)
                .put("config", value.optJSONObject("monitorConfig") ?: JSONObject.NULL).toString())
        }
        railfan.changes(value).forEach { (preferences, values) ->
            changes[preferences] = changes[preferences].orEmpty() + values
        }
        if (value.has("generatorDraft")) {
            changes[generator] = mapOf("draft" to value.optJSONObject("generatorDraft")?.toString())
        }
        val original = changes.mapValues { (preferences, values) ->
            values.mapValues { (key, _) -> preferences.all[key] }
        }
        try {
            changes.forEach { (preferences, values) -> write(preferences, values) }
            if (value.has("speedRecordings")) {
                SpeedometerStore.importHistory(context, value.getJSONArray("speedRecordings"))
            }
        } catch (error: Exception) {
            original.forEach { (preferences, values) ->
                runCatching { write(preferences, values) }.onFailure { error.addSuppressed(it) }
            }
            throw error
        }
        pending = JSONObject(value.toString())
    }

    /** Called after the WebView has reported a successful import, before any locale recreation. */
    fun complete() {
        val value = pending ?: return
        pending = null
        if (value.has("monitorConfig")) TicketMonitorService.stop(context)
        if (value.has("arrivalEnabled")) {
            val monitor = StationArrivalMonitor.get(context)
            if (value.getBoolean("arrivalEnabled")) monitor.restore() else monitor.stop()
        }
        if (value.has("language")) AppLanguages.select(value.getString("language"))
    }

    private fun write(preferences: SharedPreferences, values: Map<String, Any?>) {
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported backup preference")
            }
        }
        check(editor.commit()) { "Could not save backup preferences" }
    }
}
