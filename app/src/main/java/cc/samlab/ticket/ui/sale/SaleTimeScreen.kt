package cc.samlab.ticket.ui.sale

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.station.NativeStationBoardController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.time.Instant
import java.time.LocalTime
import cc.samlab.ticket.sale.SaleReminder
import cc.samlab.ticket.sale.todayStationSale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SaleTimeScreen(
    controller: NativeStationBoardController,
    dark: Boolean, pureBlack: Boolean, embedded: Boolean, active: Boolean, onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val text = rememberAppText()
    val repository = controller.saleTimes
    var input by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var sameCity by remember { mutableStateOf(repository.sameCity) }
    var revision by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(repository.history()) }
    var rows by remember { mutableStateOf<List<SaleTime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Instant.now()) }
    var schedule by remember { mutableStateOf<Map<String, LocalTime>>(emptyMap()) }
    val reminders = rememberSaleReminderActions(now)
    val date = now.atZone(saleZone).toLocalDate()
    val dayComplete = submitted.isEmpty() && saleDayComplete(now)
    val recommendationSlot = nextSaleSlot(now)
    val recommendationKey = if (submitted.isEmpty()) recommendationSlot else null
    val catalog = controller.saleCatalog
    val stations = remember(catalog) {
        val names = catalog.optJSONArray("names") ?: JSONArray()
        List(names.length()) { index ->
            val row = names.getJSONObject(index)
            SaleStation(row.getString("name"), row.optString("abbr"), row.optString("en"),
                row.optString("bureau"), row.optString("city"))
        }
    }
    val stationMap = remember(stations) { stations.associateBy { it.name } }
    fun search(name: String) {
        if (name !in stationMap) return
        input = name
        submitted = name
        history = repository.record(name)
        revision++
    }
    LaunchedEffect(active, date) {
        if (active) {
            controller.requestSaleCatalog()
            schedule = controller.saleSchedule.load()
            schedule = controller.saleSchedule.refresh()
        }
    }
    LaunchedEffect(active) {
        if (active) {
            history = repository.history()
            sameCity = repository.sameCity
        }
        while (active) { now = Instant.now(); delay(1000) }
    }
    LaunchedEffect(catalog, submitted, revision, date, active, recommendationKey, schedule, dayComplete) {
        if (!active) return@LaunchedEffect
        if (dayComplete) {
            rows = emptyList()
            loading = false
            error = false
            return@LaunchedEffect
        }
        loading = true
        error = false
        if (!catalog.has("names") || schedule.isEmpty()) {
            error = catalog.optBoolean("error")
            loading = !error
            return@LaunchedEffect
        }
        if (submitted.isEmpty() && (catalog.optBoolean("loading") || catalog.optBoolean("error"))) {
            error = catalog.optBoolean("error")
            loading = !error
            return@LaunchedEffect
        }
        try {
            if (submitted.isNotEmpty()) {
                rows = repository.search(submitted, stations, schedule).filter { it.name in stationMap }
            } else {
                val counts = catalog.optJSONArray("stations") ?: JSONArray()
                val traffic = List(counts.length()) {
                    counts.getJSONObject(it).let { row -> row.getString("station") to row.optInt("total") }
                }.filter { it.first in stationMap && it.second > 0 }
                rows = rankSaleSlot(traffic, schedule, now)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("SaleTimeQuery", "Unable to load sale times", failure)
            rows = emptyList()
            error = true
        } finally {
            loading = false
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text(R.string.sale_title), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, text(R.string.about_back))
                        }
                    },
                    actions = {
                        FilledTonalIconButton(
                            onClick = { onNavigate("/sale-time/reminders") },
                            modifier = Modifier.padding(end = 8.dp).testTag("sale-reminders-open"),
                        ) { Icon(Icons.Default.NotificationsActive, text(R.string.sale_reminders)) }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                SaleTimeControls(input, stations, sameCity, { input = it }, {
                    sameCity = it
                    repository.sameCity = it
                }) {
                    search(input.trim())
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp),
                    modifier = Modifier.weight(1f).testTag("sale-results"),
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "history", span = { GridItemSpan(maxLineSpan) }) {
                        SaleHistory(history, stationMap.keys, ::search) {
                            repository.clearHistory()
                            history = emptyList()
                        }
                    }
                    if (!dayComplete) item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().animateItem(
                            placementSpec = tween(360, easing = FastOutSlowInEasing),
                        )) {
                            Text(text(if (submitted.isEmpty()) R.string.sale_upcoming else R.string.sale_results),
                                Modifier.weight(1f).padding(vertical = 8.dp),
                                style = MaterialTheme.typography.titleLarge, fontFamily = projectTitleFont)
                            if (submitted.isNotEmpty()) TextButton(onClick = { input = ""; submitted = "" }) {
                                Text(text(R.string.sale_reset))
                            }
                        }
                    }
                    if (dayComplete) item(key = "day-complete", span = { GridItemSpan(maxLineSpan) }) {
                        SaleDayCompleteState(active, Modifier.fillMaxWidth().heightIn(min = 360.dp)
                            .animateItem(fadeInSpec = tween(260), fadeOutSpec = tween(220)))
                    }
                    if (loading && !dayComplete) item(span = { GridItemSpan(maxLineSpan) }) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (error && !dayComplete) item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = { controller.requestSaleCatalog(); revision++ }) {
                            Text(text(R.string.sale_retry))
                        }
                    }
                    val visible = if (dayComplete) emptyList() else if (submitted.isEmpty()) rows.filter {
                        it.time == recommendationSlot.toLocalTime()
                    }
                        else rows.filter { sameCity || it.name == submitted }
                    if (!dayComplete && !loading && !error && visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        ExpressiveEmptyState(
                            message = text(if (submitted.isEmpty()) R.string.sale_no_upcoming else R.string.sale_empty),
                            visible = active,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                                .testTag("sale-empty"),
                            artworkModifier = Modifier.testTag("sale-empty-artwork"),
                        )
                    }
                    items(visible, key = {
                        "${if (submitted.isEmpty()) recommendationSlot.toLocalDate() else date}:${it.time}:${it.name}"
                    }) { row ->
                        val saleAt = todayStationSale(row.time, now)
                        val reminder = SaleReminder(row.name, saleAt ?: 0L,
                            stationMap[row.name]?.bureau.orEmpty())
                        SaleTimeCard(
                            row, stationMap[row.name]?.bureau.orEmpty(), now,
                            if (submitted.isEmpty()) recommendationSlot.toLocalDate() else date,
                            subscribed = reminders.rows.any { it.id == reminder.id },
                            subscriptionEnabled = saleAt != null,
                            onSubscribe = if (submitted.isNotEmpty()) {
                                { reminders.toggle(reminder) }
                            } else null,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(260),
                                placementSpec = tween(360, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(220),
                            ),
                        )
                    }
                }
            }
        }
    }
}
