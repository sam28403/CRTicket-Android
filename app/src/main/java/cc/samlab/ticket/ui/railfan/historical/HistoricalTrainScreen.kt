package cc.samlab.ticket.ui.railfan.historical

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.QueryResultEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.trainquery.TrainQueryRefreshBox
import cc.samlab.ticket.ui.trainquery.TrainQueryRefreshButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoricalTrainScreen(
    web: WebView,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val text = rememberAppText()
    val repository = remember(context) { HistoricalTrainRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val online = NetworkFeatures.enabled
    var train by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf("") }
    var versions by remember { mutableStateOf(emptyList<String>()) }
    var datesLoading by remember { mutableStateOf(true) }
    var datesRetry by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<HistoricalTimetable?>(null) }
    var queryLoading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var queryFailed by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun updateVersions(available: List<String>) {
        versions = available
        if (version !in available) version = available.firstOrNull().orEmpty()
    }

    LaunchedEffect(repository, active, datesRetry) {
        if (!active) return@LaunchedEffect
        datesLoading = true
        try {
            val cached = repository.cachedVersions()
            if (cached.isNotEmpty()) updateVersions(cached)
            updateVersions(repository.refreshVersions())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the last usable list if refreshing fails.
        } finally {
            datesLoading = false
        }
    }

    fun query(code: String, date: String, refresh: Boolean = false) {
        if (queryLoading || refreshing) return
        submitted = code to date
        refreshing = refresh
        queryLoading = !refresh
        queryFailed = false
        if (!refresh) result = null
        scope.launch {
            try {
                result = repository.query(code, date)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                queryFailed = true
            } finally {
                queryLoading = false
                refreshing = false
            }
        }
    }

    val submit = { if (train.isNotBlank() && version in versions) query(train, version) }
    val canRefresh = active && online && submitted != null && !queryLoading && !refreshing
    val refresh = {
        if (canRefresh) submitted?.let { (code, date) -> query(code, date, refresh = true) }
        Unit
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text(R.string.historical_train_title), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, text(R.string.about_back))
                        }
                    },
                    actions = { if (online) TrainQueryRefreshButton(canRefresh, refresh) },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                HistoricalTrainControls(
                    train, { train = it }, version, versions, { version = it },
                    queryLoading || refreshing, submit,
                )
                Box(Modifier.weight(1f)) {
                    TrainQueryRefreshBox(offline = !online, refreshing = refreshing, onRefresh = refresh) {
                        val timetable = result
                        when {
                            queryLoading || (datesLoading && versions.isEmpty()) ->
                                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                            timetable != null && timetable.stops.isNotEmpty() -> HistoricalTrainResults(
                                web, timetable, dark,
                                error = if (queryFailed) text(R.string.historical_train_query_error) else null,
                            )
                            queryFailed -> QueryResultEmptyState(
                                text(R.string.historical_train_query_error), active, onRetry = {
                                    submitted?.let { (code, date) -> query(code, date) }
                                },
                            )
                            timetable != null -> QueryResultEmptyState(text(R.string.train_query_empty), active)
                            versions.isEmpty() -> QueryResultEmptyState(
                                text(R.string.historical_train_dates_error), active,
                                onRetry = { datesRetry++ },
                            )
                            else -> QueryResultEmptyState(text(R.string.historical_train_hint), active)
                        }
                    }
                }
            }
        }
    }
}

