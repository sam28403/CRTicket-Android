package cc.samlab.ticket.ui.trainlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveTrainListScreen(
    controller: NativeTrainListController,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    scrollView: TrainListScrollView,
    embedded: Boolean = false,
    list: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    BindTrainListScroll(scrollView.adapter, list)
    val pull = rememberPullToRefreshState()
    var calendar by remember { mutableStateOf(false) }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        SideEffect { scrollView.updateColor(colors.primary.toArgb(), colors.onSurfaceVariant.toArgb()) }
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_list), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    actions = { TrainListSortMenu(controller) },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { calendar = true },
                        modifier = Modifier.testTag("train-list-date"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(controller.date)
                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.padding(start = 4.dp))
                    }
                    val summary = when {
                        controller.loading && controller.rows.isEmpty() ->
                            stringResource(R.string.status_loading)
                        controller.error.isNotEmpty() && controller.rows.isEmpty() ->
                            stringResource(R.string.train_list_incomplete)
                        else -> stringResource(
                            R.string.train_list_total,
                            NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
                                .format(controller.rows.size),
                        )
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag("train-list-total"),
                    )
                }
                PullToRefreshBox(
                    isRefreshing = controller.loading,
                    onRefresh = { controller.request() },
                    state = pull,
                    modifier = Modifier.weight(1f).testTag("train-list-pull"),
                    indicator = {
                        TrainListRefreshIndicator(pull, controller.loading)
                    },
                ) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize().testTag("train-list")
                            .onGloballyPositioned { scrollView.updateBounds(it.boundsInRoot()) },
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (controller.error.isNotEmpty()) item(key = "error") {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                Text(controller.error, color = colors.error)
                                TextButton(onClick = { controller.request() }) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                        if (!controller.loading && controller.error.isEmpty() && controller.rows.isEmpty()) {
                            item(key = "empty") {
                                Text(stringResource(R.string.train_list_empty), Modifier.padding(24.dp))
                            }
                        }
                        itemsIndexed(
                            controller.rows,
                            key = { _, row -> "${row.sourceIndex}:${row.train}" },
                            contentType = { _, _ -> "train" },
                        ) { _, row ->
                            TrainListItem(row) { controller.open(row, onNavigate) }
                        }
                    }
                }
            }
            if (calendar) TrainListDatePicker(
                controller.date, { calendar = false }, { controller.request(it) },
            )
        }
    }
}
