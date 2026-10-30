package cc.samlab.ticket.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import cc.samlab.ticket.R

internal enum class MapSource(val id: String, val title: Int) {
    AUTO("auto", R.string.map_source_auto),
    TRANSPORT("transport", R.string.map_source_transport),
    OPENRAILWAY("openrailway", R.string.map_source_openrailway),
    OSM("osm", R.string.map_source_osm),
    HOT("hot", R.string.map_source_hot),
    FRANCE("france", R.string.map_source_france),
    TOPO("topo", R.string.map_source_topo);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

internal fun selectMapSource(context: Context, source: MapSource) {
    context.getSharedPreferences("android", Context.MODE_PRIVATE)
        .edit().putString("mapSource", source.id).apply()
}

@Composable
internal fun rememberMapSource(): MapSource {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("android", Context.MODE_PRIVATE)
    }
    var source by remember(preferences) {
        mutableStateOf(MapSource.fromId(preferences.getString("mapSource", null)))
    }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { value, key ->
            if (key == "mapSource") source = MapSource.fromId(value.getString(key, null))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return source
}
