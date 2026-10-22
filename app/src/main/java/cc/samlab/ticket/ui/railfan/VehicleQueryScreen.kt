package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import cc.samlab.ticket.ui.trainlist.NativeTrainListController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VehicleQueryScreen(
    timetable: NativeTrainListController,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    initialTrain: String = "",
    initialVehicle: String = "",
) {
    val context = LocalContext.current.applicationContext
    val historyStore = remember(context) { VehicleSearchHistory(context) }
    var history by remember { mutableStateOf(historyStore.read()) }
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var submittedByTrain by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf<VehicleSearch?>(null) }
    var rows by remember { mutableStateOf<List<VehicleHistory>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun search(value: String, forceTrain: Boolean = false, refresh: Boolean = false) {
        val parsed = if (forceTrain) VehicleSearch(value, true) else VehicleSearch.parse(value)
        invalid = parsed == null
        if (parsed == null) return
        job?.cancel()
        input = parsed.keyword
        submitted = parsed.keyword
        submittedByTrain = parsed.byTrain
        query = parsed
        if (!refresh) history = historyStore.add(parsed.keyword)
        loading = true
        refreshing = refresh
        error = false
        if (!refresh) {
            rows = emptyList()
            revision++
        }
        val token = ++generation
        job = scope.launch {
            try {
                val result = VehicleQueryRepository.search(parsed)
                if (token == generation) {
                    rows = result
                    if (!parsed.byTrain) timetable.requestVehicleRows(result, keepExisting = refresh)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) error = true
            } finally {
                if (token == generation) {
                    loading = false
                    refreshing = false
                }
            }
        }
    }

    fun clearSearch() {
        revision++
        generation++
        job?.cancel()
        input = ""
        submitted = ""
        submittedByTrain = false
        query = null
        rows = emptyList()
        loading = false
        refreshing = false
        error = false
        invalid = false
    }

    LaunchedEffect(initialTrain, initialVehicle) {
        val initial = VehicleSearch.forTrain(initialTrain)
        if (initial != null) search(initial.keyword, forceTrain = true)
        else if (VehicleSearch.forVehicle(initialVehicle) != null) search(initialVehicle)
        else if (submitted.isNotEmpty() && query == null) search(submitted, submittedByTrain)
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.railfan_vehicle), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                VehicleSearchField(
                    input, invalid,
                    onEdit = { input = it; invalid = false },
                    onClear = ::clearSearch,
                    onSearch = {
                        val sameTrain = submittedByTrain && input.trim().equals(submitted, ignoreCase = true)
                        search(input.trim().uppercase(), forceTrain = sameTrain)
                    },
                )
                if (query == null) VehicleQueryWelcome(
                        history, active, Modifier.weight(1f),
                        onClearHistory = { historyStore.clear(); history = emptyList() },
                        onSearch = { search(it) },
                    )
                else key(revision) {
                    VehicleQueryResults(
                        query!!, rows, timetable.vehicleRows, loading, refreshing, error,
                        Modifier.weight(1f),
                        onRefresh = { search(submitted, submittedByTrain, refresh = true) },
                        onRetry = { search(submitted, submittedByTrain, refresh = rows.isNotEmpty()) },
                        onVehicle = { search(it) },
                        onNavigate = onNavigate,
                        active = active,
                    )
                }
            }
        }
    }
}
