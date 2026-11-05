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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    var helpExpanded by rememberSaveable(query) { mutableStateOf(false) }
    var linesExpanded by rememberSaveable(query) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val context = LocalContext.current
    val result = controller.result
    val online = cc.samlab.ticket.network.NetworkFeatures.enabled
    val lines = rememberLineSearchResults(query, online)
    val assignments = if (online) listOfNotNull(
        VehicleSearch.forTrain(query), VehicleSearch.parse(query),
    ).distinct() else emptyList()
    val vehicleStates = assignments.associateWith { controller.assignmentState(it) }
    vehicleStates.forEach { (assignment, state) ->
        LoadSearchVehicle(assignment, active, state, timetable)
    }
    val hasVehicles = vehicleStates.values.any { it.rows.value.isNotEmpty() }
    val loadingVehicles = vehicleStates.values.any { it.loading.value }
    var vehicleExpanded by rememberSaveable(query) { mutableStateOf(false) }
    var routingExpanded by rememberSaveable(query) { mutableStateOf(false) }
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
    val routing = remember(result) {
        parseTrainListRows(context, result.optJSONArray("routing") ?: JSONArray())
    }
    val stations = remember(result) {
        val values = result.optJSONArray("stations") ?: JSONArray()
        List(values.length()) { values.getJSONObject(it) }
    }
    SearchScrollContainer(list) { scroll ->
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val dismissKeyboard: () -> Unit = {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
        val navigateResult: (String) -> Unit = { route ->
            dismissKeyboard()
            onNavigate(route)
        }

        LaunchedEffect(active) { if (active && query.isEmpty()) focus.requestFocus() }
        MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
            val colors = MaterialTheme.colorScheme
            SideEffect { scroll.updateColor(colors.primary.toArgb(), colors.onSurfaceVariant.toArgb()) }
            val trainsTitle = stringResource(R.string.search_trains)
            val stationsTitle = stringResource(R.string.search_stations)
            val settingsTitle = stringResource(R.string.home_settings)
            val helpTitle = stringResource(R.string.home_help)
            val helpMatches = searchHelp(navigateResult).filter {
                query.isNotBlank() && (it.title.contains(query.trim(), true)
                    || it.keywords.contains(query.trim(), true))
            }
            val vehicleTitle = stringResource(R.string.railfan_vehicle)
            val routingTitle = stringResource(R.string.railfan_routing)
            val linesTitle = stringResource(R.string.railfan_lines)
            val matches = searchSettings(settings, navigateResult).filter {
                query.isNotBlank() && (it.title.contains(query.trim(), ignoreCase = true)
                    || it.subtitle.contains(query.trim(), ignoreCase = true)
                    || it.keywords.contains(query.trim(), ignoreCase = true))
            }
            val extraMatches = extraSearchSettings(query, navigateResult)
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
                                        navigateResult("/train?train=${Uri.encode(row.train)}&date=${result.optString("date")}")
                                    }
                                }
                                searchResultGroup("stations", stationsTitle, query,
                                    stations, stationsExpanded, { stationsExpanded = !stationsExpanded }) { row, _, _ ->
                                    StationDiscoveryCard(row) {
                                        navigateResult("/station?station=${Uri.encode(row.optString("station"))}")
                                    }
                                }
                                searchLineResultGroup(linesTitle, query,
                                    lines, linesExpanded, { linesExpanded = !linesExpanded }) { line ->
                                    navigateResult("/railfan/lines?line=${Uri.encode(line)}")
                                }
                                searchResultGroup("settings", settingsTitle, query,
                                    matches + extraMatches, settingsExpanded, { settingsExpanded = !settingsExpanded }) { row, _, _ ->
                                    SearchSettingsRow(row, settings, dismissKeyboard)
                                }
                                searchResultGroup("help", helpTitle, query,
                                    helpMatches, helpExpanded, { helpExpanded = !helpExpanded }) { row, _, _ ->
                                    SearchSettingsRow(row, settings, dismissKeyboard)
                                }
                                searchResultGroup("routing", routingTitle, query,
                                    routing, routingExpanded, { routingExpanded = !routingExpanded }) { row, _, _ ->
                                    Box(Modifier.testTag("search-routing-card")) {
                                        TrainListItem(row) {
                                            navigateResult("/railfan/routing?train=${Uri.encode(row.train)}")
                                        }
                                    }
                                }
                                assignments.forEach { assignment ->
                                    val rows = vehicleStates.getValue(assignment).rows.value
                                    if (rows.isNotEmpty()) {
                                        val route = "/railfan/vehicle?${if (assignment.byTrain) "train" else "vehicle"}=${Uri.encode(assignment.keyword)}"
                                        item(key = "vehicle-link:${assignment.path}") {
                                            TextButton(
                                                onClick = { navigateResult(route) },
                                                modifier = Modifier.fillMaxWidth().testTag("global-vehicle-search"),
                                            ) { Text("$vehicleTitle ${assignment.keyword}") }
                                        }
                                        searchResultGroup("vehicle:${assignment.path}", vehicleTitle, query,
                                            rows, vehicleExpanded, { vehicleExpanded = !vehicleExpanded }) { row, _, _ ->
                                            Box(Modifier.testTag("global-vehicle-latest")) {
                                                if (assignment.byTrain) {
                                                    cc.samlab.ticket.ui.railfan.VehicleAssignmentCard(row) { navigateResult(route) }
                                                } else {
                                                    cc.samlab.ticket.ui.railfan.VehicleTrainHistoryCard(
                                                        row, timetable.vehicleRows["${row.day}|${row.train}"],
                                                        { train -> navigateResult("/railfan/vehicle?train=${Uri.encode(train)}") },
                                                        navigateResult,
                                                    )
                                                }
                                            }
                                        }
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
                                    && lines.isEmpty() && helpMatches.isEmpty() && routing.isEmpty() && !hasVehicles && !loadingVehicles) {
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
