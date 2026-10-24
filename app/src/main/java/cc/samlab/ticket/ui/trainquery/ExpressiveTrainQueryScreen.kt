package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.QueryResultEmptyState
import cc.samlab.ticket.ui.components.AppSwitch
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.railfan.RailfanPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveTrainQueryScreen(
    controller: NativeTrainQueryController,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    embedded: Boolean = false,
    active: Boolean = true,
    location: String = "/train",
) {
    val textResources = rememberAppText()
    val railfanEnabled by RailfanPreferences.rememberEnabled()
    val state = controller.stateFor(location)
    val loading = state.optBoolean("loading")
    val result = state.optJSONObject("result")
    val stops = result?.optJSONArray("stops")
    val count = stops?.length() ?: 0
    val error = state.optString("error")
    val timing by rememberQueryTiming(result, active)
    val operatingCalendar by rememberOperatingCalendar(
        train = if (count > 0) result!!.optString("train") else "",
        date = result?.optString("date").orEmpty(),
    )
    var datePicker by remember { mutableStateOf(false) }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize().testTag("train-query-screen"), color = colors.surfaceContainer) {
            Column {
                TopAppBar(
                    title = { Text(textResources(R.string.home_train), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, textResources(R.string.about_back))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                TrainQueryControls(
                    controller, state, controller.state.optString("location") == location,
                    onDate = { datePicker = true },
                )
                if (!loading && count == 0 && (error.isNotEmpty() || result != null)) {
                    QueryResultEmptyState(
                        message = if (error.isNotEmpty()) error
                            else textResources(R.string.train_query_empty),
                        active = active,
                        modifier = Modifier.weight(1f).testTag("train-query-empty"),
                        onRetry = if (error.isNotEmpty()) {
                            { controller.dispatch("query") }
                        } else null,
                    )
                } else LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("train-query-content"),
                ) {
                    if (loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                    }
                    if (state.optString("error").isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(state.optString("error"), Modifier.padding(16.dp), color = colors.error)
                    }
                    if (count > 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("train-query-summary"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(result!!.optString("train"), style = MaterialTheme.typography.titleLarge, fontFamily = projectTitleFont)
                                    Text("${result.optString("from")} → ${result.optString("to")}", style = MaterialTheme.typography.titleMedium)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        textResources(R.string.tracking_action_start),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    AppSwitch(
                                        checked = state.optBoolean("tracking"),
                                        onCheckedChange = { controller.dispatch("tracking") },
                                        enabled = state.optBoolean("trackingReady"),
                                        modifier = Modifier.testTag("train-query-tracking"),
                                    )
                                }
                            }
                        }
                        items(count, key = { it }) { index ->
                            TrainQueryStopCard(
                                stops!!.getJSONObject(index), index, count,
                                timing.highlight(index), dark, onNavigate,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = { controller.dispatch("export") },
                                shapes = ButtonDefaults.shapes(),
                                enabled = !state.optBoolean("exporting"),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("train-query-export"),
                            ) { Text(textResources(R.string.train_query_export)) }
                        }
                        if (operatingCalendar.days != null) item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                TrainOperatingCalendar(operatingCalendar, result!!.optString("date"), dark)
                            }
                        }
                        state.optJSONObject("route")?.let { route ->
                            item(span = { GridItemSpan(maxLineSpan) }) { TrainQueryRouteMap(route, result!!.optString("train")) }
                        }
                        state.optJSONObject("nextRouting")?.takeIf { railfanEnabled }?.let { next ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TrainQueryNextRouting(next, result!!.optString("date"), onNavigate)
                            }
                        }
                    } else if (!loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (result != null) textResources(R.string.train_query_empty) else textResources(R.string.train_query_hint),
                            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (result == null && !loading) {
                        trainQueryDiscovery(controller, state, onNavigate)
                    }
                }
            }
        }
        if (datePicker) TrainQueryDatePicker(
            controller.date, { datePicker = false }, { controller.edit(day = it) },
        )
    }
}
