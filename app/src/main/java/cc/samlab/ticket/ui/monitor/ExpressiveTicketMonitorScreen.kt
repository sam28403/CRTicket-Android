package cc.samlab.ticket.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.settings.SettingsCollapsedHeight
import cc.samlab.ticket.ui.settings.SettingsExpandedHeight
import cc.samlab.ticket.ui.settings.SettingsHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveTicketMonitorScreen(
    controller: NativeTicketMonitorController,
    dark: Boolean,
    pureBlack: Boolean,
    detail: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    embedded: Boolean = false,
    active: Boolean = true,
) {
    val textResources = rememberAppText()
    val range = with(LocalDensity.current) { (SettingsExpandedHeight - SettingsCollapsedHeight).toPx() }
    val appBar = rememberTopAppBarState(-range, -range)
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBar)
    val grid = rememberLazyGridState()
    val pull = rememberPullToRefreshState()
    LaunchedEffect(detail, active) {
        if (active) controller.action("sync")
    }
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(
            Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)
                .testTag(if (detail) "monitor-settings-screen" else "monitor-screen"),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Column {
                    SettingsHeader(
                        behavior, onBack, embedded && !detail,
                        title = if (detail) textResources(R.string.monitor_settings_title) else textResources(R.string.monitor_title),
                        actions = {
                            if (!detail) FilledTonalIconButton(
                                onSettings, Modifier.padding(end = 8.dp).testTag("monitor-settings"),
                            ) { Icon(Icons.Default.Settings, textResources(R.string.monitor_settings_title)) }
                        },
                    )
                    Box(Modifier.weight(1f)) {
                        if (detail) MonitorSettingsContent(controller, embedded)
                        else MonitorResultsContent(controller, grid, appBar, pull, active)
                    }
                }
                if (!detail && active) MonitorRefreshIndicator(pull, controller.loading)
            }
        }
    }
}
