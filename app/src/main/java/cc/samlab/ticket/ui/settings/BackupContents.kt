package cc.samlab.ticket.ui.settings

import cc.samlab.ticket.R
import org.json.JSONArray
import org.json.JSONObject

internal enum class BackupContent(
    val id: String,
    val label: Int,
    val storageKeys: List<String> = emptyList(),
    val nativeKeys: List<String> = emptyList(),
) {
    TICKETS("tickets", R.string.backup_content_tickets),
    SEARCH(
        "search", R.string.backup_content_search,
        listOf("crticket-train-query-history", "crticket-station-query-history"),
        listOf("vehicleSearchHistory", "saleSearchHistory"),
    ),
    SETTINGS(
        "settings", R.string.backup_content_settings,
        listOf("crticket-important-notice-hidden", "mapSource"),
        listOf("language", "arrivalEnabled", "importantNoticeSuppressed", "railfanToolsEnabled",
            "saleAlarmEnabled", "saleSameCity", "mapSource"),
    ),
    PROFILE("profile", R.string.backup_content_profile),
    VISITS("visits", R.string.backup_content_visits, nativeKeys = listOf("stationVisits")),
    SPEED("speed", R.string.backup_content_speed, nativeKeys = listOf("speedRecordings")),
    DRAFT(
        "draft", R.string.backup_content_draft,
        listOf("crticket-generator-draft"), listOf("generatorDraft"),
    ),
    MONITOR("monitor", R.string.backup_content_monitor, nativeKeys = listOf("monitorConfig")),
}

internal fun availableBackupContents(value: Any): Set<BackupContent> {
    if (value is JSONArray) return setOf(BackupContent.TICKETS)
    val backup = value as? JSONObject ?: return emptySet()
    if (backup.optInt("schemaVersion", 1) == 1) return setOf(BackupContent.TICKETS)
    require(backup.optString("app") == "CRTicket" && backup.optInt("schemaVersion") == 2)
    val declared = backup.optJSONArray("contents")
    val settings = backup.optJSONObject("settings")
    return BackupContent.entries.filter { item ->
        val included = declared == null || (0 until declared.length()).any {
            declared.optString(it) == item.id
        }
        included && when (item) {
            BackupContent.TICKETS -> backup.has("tickets")
            BackupContent.PROFILE -> settings?.has("profile") == true
            else -> (item == BackupContent.SETTINGS && settings?.has("theme") == true) ||
                item.storageKeys.any { settings?.optJSONObject("storage")?.has(it) == true } ||
                item.nativeKeys.any { settings?.optJSONObject("native")?.has(it) == true }
        }
    }.toSet()
}
