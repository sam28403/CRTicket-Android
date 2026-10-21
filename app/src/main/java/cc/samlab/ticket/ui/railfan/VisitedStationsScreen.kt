package cc.samlab.ticket.ui.railfan

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.arrival.ArrivalHistory
import cc.samlab.ticket.arrival.StationVisit
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.station.NativeStationBoardController
import cc.samlab.ticket.ui.station.StationDiscoveryCard
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VisitedStationsScreen(
    controller: NativeStationBoardController,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val history = remember(context) { ArrivalHistory(context) }
    var visits by remember(history) { mutableStateOf(history.visits()) }
    LaunchedEffect(active, visits) {
        if (active && visits.isNotEmpty()) {
            while (true) {
                controller.requestVisitCards(visits.map { it.station })
                delay(60_000)
            }
        }
    }
    DisposableEffect(history) {
        val preferences = context.getSharedPreferences("station-arrival", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "firstVisits") visits = history.visits()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        visits = history.visits()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.railfan_visits), fontFamily = projectTitleFont)
                    },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.about_back),
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                if (visits.isEmpty()) {
                    ExpressiveEmptyState(
                        message = stringResource(R.string.visits_empty),
                        visible = active,
                        modifier = Modifier.fillMaxSize().testTag("visits-empty"),
                    )
                } else VisitTimeline(visits, controller, onNavigate, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VisitTimeline(
    visits: List<StationVisit>,
    controller: NativeStationBoardController,
    onNavigate: (String) -> Unit,
    modifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val groups = visits.sortedByDescending { it.firstArrival }.groupBy {
        Instant.ofEpochMilli(it.firstArrival).atZone(zone).toLocalDate()
    }
    val formatter = if (locale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("yyyy年M月d日", locale)
    } else DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Match the station discovery cards' minimum width on phones and tablets.
        val columns = ((maxWidth - 32.dp + 8.dp) / 328.dp).toInt().coerceAtLeast(1)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().testTag("visits-timeline"),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { (date, stations) ->
                item(key = "date:$date", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        date.format(formatter),
                        Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
                            .testTag("visit-date:$date"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(stations, key = { it.station }) { visit ->
                    val summary = controller.visitCards[visit.station]
                    StationDiscoveryCard(
                        row = summary ?: JSONObject().put("station", visit.station),
                        status = if (summary != null) null else stringResource(
                            if (controller.visitsLoading) R.string.status_loading
                            else R.string.train_list_network_error,
                        ),
                        tag = "visited-station:${visit.station}",
                    ) {
                        onNavigate("/station?station=${Uri.encode(visit.station)}")
                    }
                }
            }
        }
    }
}
