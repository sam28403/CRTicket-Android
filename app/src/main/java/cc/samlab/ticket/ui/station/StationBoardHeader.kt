package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.projectTitleFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StationBoardHeader(
    controller: StationBoardState,
    embedded: Boolean,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text(
                    "车站大屏",
                    Modifier.padding(start = 16.dp, end = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = projectTitleFont,
                )
                StationBoardSearch(controller, Modifier.weight(1f))
            }
        } else Column {
            TopAppBar(
                title = { Text("车站大屏", fontFamily = projectTitleFont) },
                navigationIcon = {
                    if (!embedded) FilledTonalIconButton(onBack, Modifier.padding(start = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
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
