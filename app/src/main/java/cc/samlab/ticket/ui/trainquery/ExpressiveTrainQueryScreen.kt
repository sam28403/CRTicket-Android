package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.home.homeColorScheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveTrainQueryScreen(
    controller: NativeTrainQueryController,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    embedded: Boolean = false,
) {
    val state = controller.state
    val loading = state.optBoolean("loading")
    val result = state.optJSONObject("result")
    val stops = result?.optJSONArray("stops")
    val count = stops?.length() ?: 0
    var datePicker by remember { mutableStateOf(false) }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize().testTag("train-query-screen"), color = colors.surfaceContainer) {
            Column {
                TopAppBar(
                    title = { Text("车次查询", fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                TrainQueryControls(controller, onDate = { datePicker = true })
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("train-query-content"),
                ) {
                    if (loading) item {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                    }
                    if (state.optString("error").isNotEmpty()) item {
                        Text(state.optString("error"), Modifier.padding(16.dp), color = colors.error)
                    }
                    if (count > 0) {
                        item {
                            Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                                Text(result!!.optString("train"), style = MaterialTheme.typography.titleLarge, fontFamily = projectTitleFont)
                                Text("${result.optString("from")} → ${result.optString("to")}", style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("追踪", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    Switch(
                                        checked = state.optBoolean("tracking"),
                                        onCheckedChange = { controller.dispatch("tracking") },
                                        enabled = state.optBoolean("trackingReady"),
                                        modifier = Modifier.testTag("train-query-tracking"),
                                    )
                                }
                            }
                        }
                        items(count, key = { it }) { index ->
                            TrainQueryStopCard(stops!!.getJSONObject(index), index, count, onNavigate)
                        }
                        item {
                            TextButton(
                                onClick = { controller.dispatch("export") },
                                enabled = !state.optBoolean("exporting"),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("导出数据") }
                        }
                        state.optJSONObject("route")?.let { route ->
                            item { TrainQueryRouteMap(route, result!!.optString("train")) }
                        }
                    } else if (!loading) item {
                        Text(
                            if (result != null) "该日期未查询到此车次" else "输入车次和始发日期查询时刻表",
                            Modifier.padding(24.dp), color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (datePicker) TrainQueryDatePicker(
            controller.date, { datePicker = false }, { controller.edit(day = it) },
        )
    }
}
