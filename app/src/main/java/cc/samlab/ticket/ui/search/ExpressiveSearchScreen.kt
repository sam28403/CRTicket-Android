package cc.samlab.ticket.ui.search

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.settings.NativeSettingsController
import cc.samlab.ticket.ui.settings.SettingsDialogs
import cc.samlab.ticket.ui.station.StationDiscoveryCard
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.NativeTrainListController
import cc.samlab.ticket.ui.trainlist.parseTrainListRows
import kotlinx.coroutines.delay
import org.json.JSONArray
import cc.samlab.ticket.ui.railfan.RailfanPreferences
import cc.samlab.ticket.ui.railfan.VehicleSearch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveSearchScreen(
    controller: NativeSearchController,
    settings: NativeSettingsController,
    dark: Boolean,
    pureBlack: Boolean,
    active: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    timetable: NativeTrainListController,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var trainsExpanded by rememberSaveable(query) { mutableStateOf(false) }
    var stationsExpanded by rememberSaveable(query) { mutableStateOf(false) }
    var settingsExpanded by rememberSaveable(query) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val context = LocalContext.current
    val result = controller.result
    val railfan by RailfanPreferences.rememberEnabled()
    var assignmentFailed by rememberSaveable(query, railfan) { mutableStateOf(false) }
    val trainsLoaded = !controller.loading && controller.completedQuery == query.trim()
        && !result.optBoolean("error")
    val assignment = if (railfan && !assignmentFailed && trainsLoaded) {
        VehicleSearch.forTrain(query) ?: VehicleSearch.forVehicle(query)
    } else null
    LaunchedEffect(query, active) {
        if (active && controller.prepareSearch(query)) {
            // Invalidate the previous request before debouncing the next one.
            list.scrollToItem(0)
            if (query.isNotBlank()) {
                delay(180)
                controller.search(query.trim())
            }
        }
    }
    val trains = remember(result) {
        parseTrainListRows(context, result.optJSONArray("trains") ?: JSONArray())
    }
    val stations = remember(result) {
        val values = result.optJSONArray("stations") ?: JSONArray()
        List(values.length()) { values.getJSONObject(it) }
    }
    SearchScrollContainer(list) { scroll ->
        LaunchedEffect(active) { if (active && query.isEmpty()) focus.requestFocus() }
        MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
            val colors = MaterialTheme.colorScheme
            SideEffect { scroll.updateColor(colors.primary.toArgb(), colors.onSurfaceVariant.toArgb()) }
            val trainsTitle = stringResource(R.string.search_trains)
            val stationsTitle = stringResource(R.string.search_stations)
            val settingsTitle = stringResource(R.string.home_settings)
            val matches = searchSettings(settings, onNavigate).filter {
                query.isNotBlank() && (it.title.contains(query.trim(), ignoreCase = true)
                    || it.subtitle.contains(query.trim(), ignoreCase = true))
            }
            Surface(Modifier.fillMaxSize().testTag("global-search-screen"),
                color = MaterialTheme.colorScheme.surfaceContainer) {
                BoxWithConstraints(contentAlignment = Alignment.TopCenter) {
                    val compactEmpty = maxHeight < 480.dp
                    // WebViewLayout already resizes the shared native host above the IME.
                    Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        Box(Modifier.padding(16.dp)) {
                            SearchField(query, { query = it }, onBack, focus)
                        }
                        LazyColumn(
                            state = list,
                            modifier = Modifier.weight(1f).testTag("global-search-results")
                                .onGloballyPositioned { scroll.updateBounds(it.boundsInRoot()) },
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (query.isBlank()) {
                                item {
                                    SearchEmptyState(R.string.search_empty, R.string.search_empty_hint,
                                        active, compactEmpty)
                                }
                            } else {
                                searchResultGroup("trains", trainsTitle, query,
                                    trains, trainsExpanded, { trainsExpanded = !trainsExpanded }) { row, _, _ ->
                                    TrainListItem(row) {
                                        onNavigate("/train?train=${Uri.encode(row.train)}&date=${result.optString("date")}")
                                    }
                                }
                                searchResultGroup("stations", stationsTitle, query,
                                    stations, stationsExpanded, { stationsExpanded = !stationsExpanded }) { row, _, _ ->
                                    StationDiscoveryCard(row) {
                                        onNavigate("/station?station=${Uri.encode(row.optString("station"))}")
                                    }
                                }
                                searchResultGroup("settings", settingsTitle, query,
                                    matches, settingsExpanded, { settingsExpanded = !settingsExpanded }) { row, _, _ ->
                                    SearchSettingsRow(row, settings)
                                }
                                if (assignment != null) {
                                    item(key = "vehicle:${assignment.keyword}") {
                                        SearchVehicleAssignment(
                                            assignment,
                                            active,
                                            onNavigate,
                                            onFailure = { assignmentFailed = true },
                                            timetable = timetable,
                                            state = controller.assignmentState(assignment),
                                        )
                                    }
                                }
                                if (controller.loading) {
                                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                } else if (result.optBoolean("error")) {
                                    item {
                                        TextButton(onClick = { controller.search(query) }, Modifier.fillMaxWidth()) {
                                            Text(stringResource(R.string.search_error))
                                        }
                                    }
                                } else if (trains.isEmpty() && stations.isEmpty() && matches.isEmpty()
                                    && assignment == null) {
                                    item {
                                        SearchEmptyState(R.string.search_no_results, R.string.search_no_results_hint,
                                            active, compactEmpty)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (active) SettingsDialogs(settings.ui.dialog, settings.ui::dismiss, settings)
        }
    }
}
