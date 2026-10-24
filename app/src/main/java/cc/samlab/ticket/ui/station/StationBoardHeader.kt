package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StationBoardHeader(
    controller: StationBoardState,
    embedded: Boolean,
    onBack: () -> Unit,
) {
    val textResources = rememberAppText()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, textResources(R.string.about_back))
                }
                Text(
                    textResources(R.string.home_station),
                    Modifier.padding(start = 16.dp, end = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = projectTitleFont,
                )
                StationBoardSearch(controller, Modifier.weight(1f))
                StationBoardRefreshButton(controller)
            }
        } else Column {
            TopAppBar(
                title = { Text(textResources(R.string.home_station), fontFamily = projectTitleFont) },
                navigationIcon = {
                    if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, textResources(R.string.about_back))
                    }
                },
                actions = { StationBoardRefreshButton(controller) },
                expandedHeight = 48.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            StationBoardSearch(controller)
        }
    }
}

@Composable
private fun StationBoardRefreshButton(controller: StationBoardState) {
    val text = rememberAppText()
    IconButton(
        onClick = controller::refresh,
        enabled = !controller.loading && controller.resultStation.isNotEmpty(),
        modifier = Modifier.testTag("station-refresh"),
    ) {
        Icon(Icons.Default.Refresh, text(R.string.station_refresh))
    }
}
