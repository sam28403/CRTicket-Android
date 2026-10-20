package cc.samlab.ticket.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.settings.SettingsCollapsedHeight
import cc.samlab.ticket.ui.settings.SettingsExpandedHeight
import cc.samlab.ticket.ui.settings.SettingsHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveStatisticsScreen(
    body: StatisticsBodyLayout,
    state: StatisticsUiState,
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    embedded: Boolean,
    visible: Boolean,
    onMonthSelected: (String) -> Unit,
) {
    val textResources = rememberAppText()
    val collapseRange = with(LocalDensity.current) {
        (SettingsExpandedHeight - SettingsCollapsedHeight).toPx()
    }
    val appBar = rememberTopAppBarState(
        initialHeightOffsetLimit = -collapseRange,
        initialHeightOffset = -collapseRange,
    )
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBar, canScroll = { visible })
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(visible) {
        if (visible) {
            appBar.heightOffset = appBar.heightOffsetLimit
            appBar.contentOffset = 0f
        }
    }
    DisposableEffect(body, behavior, visible, state.empty) {
        body.onScroll = { delta, atTop ->
            if (visible && !state.empty) {
                settle?.cancel()
                // The WebView owns content scrolling; mirror only the app bar portion natively.
                if (delta < 0f) behavior.nestedScrollConnection.onPreScroll(
                    Offset(0f, delta), NestedScrollSource.UserInput,
                ) else if (atTop) behavior.nestedScrollConnection.onPostScroll(
                    Offset.Zero, Offset(0f, delta), NestedScrollSource.UserInput,
                )
            }
        }
        body.onScrollEnd = {
            settle = scope.launch {
                behavior.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity.Zero)
            }
        }
        onDispose {
            settle?.cancel()
            body.onScroll = null
            body.onScrollEnd = null
        }
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxSize().testTag("statistics-screen")
                .nestedScroll(behavior.nestedScrollConnection),
            color = colors.surfaceContainer,
        ) {
            Column {
                Box(Modifier.testTag("statistics-header").semantics {
                    stateDescription = if (appBar.collapsedFraction < 0.5f) textResources(R.string.state_expanded) else textResources(R.string.state_collapsed)
                }) {
                    SettingsHeader(behavior, onBack, embedded, stringResource(R.string.home_data))
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(factory = { body }, modifier = Modifier.fillMaxSize())
                    if (state.empty && !state.loading) {
                        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
                            StatisticsEmptyState(visible)
                        }
                    }
                }
            }
        }
        state.monthRequest?.let { month ->
            androidx.compose.runtime.key(state.monthPickerSession) {
                if (visible) StatisticsMonthPicker(month, state::dismissMonth, onMonthSelected)
            }
        }
    }
}
