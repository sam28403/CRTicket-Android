package cc.samlab.ticket.ui.railfan.historical

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.trainquery.QueryStopHighlight
import cc.samlab.ticket.ui.trainquery.TrainQueryRouteMap
import cc.samlab.ticket.ui.trainquery.TrainQueryStopCard
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

@Composable
internal fun HistoricalTrainResults(
    web: WebView,
    timetable: HistoricalTimetable,
    dark: Boolean,
    error: String?,
) {
    val stops = timetable.stops
    val route by produceState<JSONObject?>(null, web, stops) {
        val updated = buildRoute(web, stops)
        if (updated?.toString() != value?.toString()) value = updated
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        modifier = Modifier.fillMaxSize().testTag("historical-train-results"),
    ) {
        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(error, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text(
                    timetable.train, style = MaterialTheme.typography.titleLarge,
                    fontFamily = projectTitleFont,
                )
                Text(
                    "${stops.first().optString("station")} → ${stops.last().optString("station")}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        items(stops.size, key = { it }) { index ->
            TrainQueryStopCard(stops[index], index, stops.size, QueryStopHighlight.NONE, dark, onNavigate = null)
        }
        route?.let { map ->
            item(key = "route-map", span = { GridItemSpan(maxLineSpan) }) {
                TrainQueryRouteMap(map, timetable.train, R.string.historical_train_map_disclaimer)
            }
        }
    }
}

private suspend fun buildRoute(web: WebView, stops: List<JSONObject>): JSONObject? =
    suspendCancellableCoroutine { continuation ->
        val payload = JSONObject.quote(JSONArray(stops).toString())
        web.evaluateJavascript("window.crticketBuildTrainRoute?.(JSON.parse($payload))") { result ->
            if (continuation.isActive) {
                continuation.resume(runCatching { JSONObject(result) }.getOrNull())
            }
        }
    }
