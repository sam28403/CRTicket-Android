package cc.samlab.ticket.backup

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Only portable choices are backed up; download IDs and installation state belong to this device. */
internal class BackupUpdateSettings(context: Context) {
    private val preferences = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val fields = mapOf("updatePreviews" to "previews", "updateAutomatic" to "automatic")

    fun snapshot(value: JSONObject): JSONObject = value
        .put("updatePreviews", preferences.getBoolean("previews", false))
        .put("updateAutomatic", preferences.getBoolean("automatic", true))

    fun validate(value: JSONObject) {
        fields.keys.forEach { key ->
            require(!value.has(key) || value.get(key) is Boolean) { "Invalid backup preference: $key" }
        }
    }

    fun changes(value: JSONObject): Map<SharedPreferences, Map<String, Any?>> {
        val values = fields.filterKeys(value::has).map { (field, key) -> key to value.getBoolean(field) }.toMap()
        return if (values.isEmpty()) emptyMap() else mapOf(preferences to values)
    }
}
