package cc.samlab.ticket

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MonitorPreferenceSnapshot {
    private val names = listOf("train-tracking", "ticket-monitor")

    fun capture(context: Context): JSONObject = JSONObject().apply {
        names.forEach { name ->
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                val encoded = JSONObject()
                when (value) {
                    is String -> encoded.put("type", "string").put("value", value)
                    is Boolean -> encoded.put("type", "boolean").put("value", value)
                    is Int -> encoded.put("type", "int").put("value", value)
                    is Long -> encoded.put("type", "long").put("value", value)
                    is Float -> encoded.put("type", "float").put("value", value)
                    is Set<*> -> encoded.put("type", "set")
                        .put("value", JSONArray(value.toList()))
                    else -> error("Unsupported preference type for $name/$key")
                }
                values.put(key, encoded)
            }
            put(name, values)
        }
    }

    fun restore(context: Context, snapshot: JSONObject) {
        names.forEach { name ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            val values = snapshot.getJSONObject(name)
            values.keys().forEach { key ->
                val encoded = values.getJSONObject(key)
                when (encoded.getString("type")) {
                    "string" -> editor.putString(key, encoded.getString("value"))
                    "boolean" -> editor.putBoolean(key, encoded.getBoolean("value"))
                    "int" -> editor.putInt(key, encoded.getInt("value"))
                    "long" -> editor.putLong(key, encoded.getLong("value"))
                    "float" -> editor.putFloat(key, encoded.getDouble("value").toFloat())
                    "set" -> {
                        val set = encoded.getJSONArray("value")
                        editor.putStringSet(key, (0 until set.length())
                            .map { set.getString(it) }.toSet())
                    }
                }
            }
            check(editor.commit()) { "Could not restore $name preferences" }
        }
    }
}
