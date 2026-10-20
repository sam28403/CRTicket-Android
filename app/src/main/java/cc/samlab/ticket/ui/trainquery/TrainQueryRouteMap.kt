package cc.samlab.ticket.ui.trainquery

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun TrainQueryRouteMap(route: JSONObject, train: String) {
    val textResources = rememberAppText()
    val assets = LocalContext.current.assets
    val outline by produceState(emptyList<List<GeoPoint>>(), assets) {
        value = withContext(Dispatchers.IO) { runCatching { readRouteOutline(assets) }.getOrDefault(emptyList()) }
    }
    val points = route.optJSONArray("points")
    val stops = remember(route) { List(points?.length() ?: 0) { points!!.getJSONObject(it) } }
    val coordinates = remember(route) { stops.map {
        val value = it.getJSONArray("value")
        GeoPoint(value.getDouble(0).toFloat(), value.getDouble(1).toFloat())
    } }
    var zoom by remember(route) { mutableFloatStateOf(1f) }
    var pan by remember(route) { mutableStateOf(Offset.Zero) }
    var selected by remember(route) { mutableStateOf<JSONObject?>(null) }
    val colors = MaterialTheme.colorScheme
    val horizontalPadding = with(LocalDensity.current) { 96.dp.toPx() }
    val verticalPadding = with(LocalDensity.current) { 112.dp.toPx() }
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    textResources(R.string.train_map_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text(textResources(R.string.train_map_center)) }
            }
            val missing = route.optJSONArray("missingStations")
            if (missing != null && missing.length() > 0) Text(
                textResources(
                    R.string.train_map_missing_coordinates,
                    List(missing.length()) { missing.getString(it) }.joinToString("、"),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (coordinates.isEmpty()) Text(textResources(R.string.train_map_empty)) else {
                val centerX = (coordinates.minOf { it.longitude } + coordinates.maxOf { it.longitude }) / 2
                val centerY = (coordinates.minOf { it.latitude } + coordinates.maxOf { it.latitude }) / 2
                val spanX = (coordinates.maxOf { it.longitude } - coordinates.minOf { it.longitude }).coerceAtLeast(0.2f)
                val spanY = (coordinates.maxOf { it.latitude } - coordinates.minOf { it.latitude }).coerceAtLeast(0.2f)
                fun project(point: GeoPoint, width: Float, height: Float): Offset {
                    val scale = min(
                        (width - horizontalPadding).coerceAtLeast(1f) / spanX,
                        (height - verticalPadding).coerceAtLeast(1f) / (spanY / 0.75f),
                    ) * zoom
                    return Offset(
                        width / 2 + (point.longitude - centerX) * scale,
                        height / 2 - (point.latitude - centerY) * scale / 0.75f,
                    ) + pan
                }
                Canvas(
                    Modifier.fillMaxWidth().height(380.dp).clipToBounds()
                        .semantics { contentDescription = textResources(R.string.train_map_description, train) }
                        .pointerInput(route) {
                            detectTransformGestures { centroid, delta, scale, _ ->
                                val next = (zoom * scale).coerceIn(0.5f, 12f)
                                val origin = Offset(size.width / 2f, size.height / 2f)
                                pan = centroid - origin - (centroid - origin - pan) * (next / zoom) + delta
                                zoom = next
                            }
                        }
                        .pointerInput(route, zoom, pan) {
                            detectTapGestures { tap ->
                                val index = coordinates.indices.minByOrNull {
                                    (project(coordinates[it], size.width.toFloat(), size.height.toFloat()) - tap).getDistance()
                                }
                                if (index != null && (project(coordinates[index], size.width.toFloat(), size.height.toFloat()) - tap).getDistance() < 36.dp.toPx()) {
                                    selected = stops[index]
                                }
                            }
                        },
                ) {
                    fun point(value: GeoPoint) = project(value, size.width, size.height)
                    outline.forEach { ring ->
                        val path = Path()
                        ring.forEachIndexed { index, geo ->
                            val p = point(geo)
                            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        path.close()
                        drawPath(path, colors.surfaceContainer)
                        drawPath(path, colors.outlineVariant, style = Stroke(1.dp.toPx()))
                    }
                    val lines = route.optJSONArray("lines")
                    repeat(lines?.length() ?: 0) { index ->
                        val pair = lines!!.getJSONObject(index).getJSONArray("coords")
                        fun end(i: Int): Offset {
                            val v = pair.getJSONArray(i)
                            return point(GeoPoint(v.getDouble(0).toFloat(), v.getDouble(1).toFloat()))
                        }
                        drawLine(colors.primary, end(0), end(1), 3.dp.toPx())
                    }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.dp.toPx() }
                    coordinates.forEachIndexed { index, geo ->
                        val p = point(geo)
                        drawCircle(colors.primary, if (index == 0 || index == coordinates.lastIndex) 6.dp.toPx() else 4.dp.toPx(), p)
                        val name = stops[index].optString("name")
                        val x = (p.x + 8.dp.toPx()).coerceIn(0f, (size.width - paint.measureText(name)).coerceAtLeast(0f))
                        val y = p.y + if (index % 2 == 0) -8.dp.toPx() else 18.dp.toPx()
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 3.dp.toPx()
                        paint.color = colors.surfaceContainerLowest.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(name, x, y, paint)
                        paint.style = Paint.Style.FILL
                        paint.color = colors.onSurface.toArgb()
                        drawContext.canvas.nativeCanvas.drawText(name, x, y, paint)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = textResources(R.string.label_hint),
                    modifier = Modifier.size(18.dp),
                )
                Text(textResources(R.string.train_map_disclaimer), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    selected?.let { stop ->
        val arrival = if (stop.optBoolean("isFirst")) textResources(R.string.station_origin) else stop.optString("arrival", "—")
        val departure = if (stop.optBoolean("isLast")) textResources(R.string.station_terminal) else stop.optString("departure", "—")
        val day = stop.optInt("arrivalDay")
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${stop.optString("no")}. ${stop.optString("name")}") },
            text = {
                Text(textResources(
                    R.string.train_map_stop_times,
                    arrival,
                    if (day > 0) textResources(R.string.calendar_day_offset, day) else "",
                    departure,
                ))
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(textResources(R.string.action_confirm)) } },
        )
    }
}
