package cc.samlab.ticket.ui.station

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
import org.json.JSONArray

@Composable
internal fun StationBoardDiscovery(
    controller: StationBoardState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textResources = rememberAppText()
    val state = controller.discovery
    val history = state.optJSONArray("history") ?: JSONArray()
    fun open(name: String) { onNavigate("/station?station=${Uri.encode(name)}") }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
        modifier = modifier.fillMaxWidth().testTag("station-discovery"),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "history", span = { GridItemSpan(maxLineSpan) }) {
            var displayed by remember { mutableStateOf(history) }
            SideEffect { if (history.length() > 0) displayed = history }
            AnimatedVisibility(
                visible = history.length() > 0,
                exit = shrinkVertically(tween(300)) + fadeOut(tween(180)),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth().testTag("station-query-history"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(displayed.length()) { index ->
                        val name = displayed.getString(index)
                        OutlinedButton(onClick = { open(name) }) { Text(name) }
                    }
                    TextButton(
                        onClick = { controller.discoveryAction("clearHistory") },
                        modifier = Modifier.testTag("station-clear-history"),
                    ) { Text(textResources(R.string.action_clear)) }
                }
            }
        }
        for ((key, title) in listOf(
            "busy" to textResources(R.string.station_discovery_busy),
            "recommendations" to textResources(R.string.discovery_today),
        )) {
            item(key = "$key-title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    title, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
                        .testTag("station-$key-title"),
                    style = MaterialTheme.typography.titleLarge, fontFamily = projectTitleFont,
                )
            }
            val rows = state.optJSONArray(key) ?: JSONArray()
            if (state.optBoolean("loading")) {
                item(key = "$key-loading", span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            } else if (state.optString("error").isNotEmpty()) {
                item(key = "$key-error", span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { controller.discoveryAction("recommendations") }) {
                        Text(textResources(R.string.station_discovery_error))
                    }
                }
            } else if (rows.length() == 0) {
                item(key = "$key-empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        textResources(R.string.station_discovery_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(rows.length(), key = { "$key-${rows.getJSONObject(it).optString("station")}" }) { index ->
                    val row = rows.getJSONObject(index)
                    StationDiscoveryCard(
                        row = row,
                        rank = if (key == "busy") index + 1 else null,
                    ) { open(row.optString("station")) }
                }
            }
        }
    }
}
