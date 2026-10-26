package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.station.StationBoardMap

@Composable
internal fun MapSettingsContent(dark: Boolean, visible: Boolean) {
    val selected = rememberMapSource()
    val candidates = MapSource.entries.filter { it != MapSource.AUTO }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(16.dp).selectableGroup().testTag("settings-map-list"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (visible) StationBoardMap(
            station = "北京",
            dark = dark,
            modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp)
                .testTag("settings-map-preview"),
        )
        MapSourceChoice(MapSource.AUTO, selected, 0, 1)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().testTag("map-source-candidates"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            candidates.forEachIndexed { index, source ->
                MapSourceChoice(source, selected, index, candidates.size)
            }
        }
    }
}

@Composable
private fun MapSourceChoice(source: MapSource, selected: MapSource, index: Int, count: Int) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (source == selected) colors.secondaryContainer else colors.surfaceContainerLowest,
        shape = RoundedCornerShape(
            topStart = if (index == 0) 28.dp else 4.dp,
            topEnd = if (index == 0) 28.dp else 4.dp,
            bottomStart = if (index == count - 1) 28.dp else 4.dp,
            bottomEnd = if (index == count - 1) 28.dp else 4.dp,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .testTag("map-source:${source.id}")
                .selectable(source == selected, role = Role.RadioButton) {
                    selectMapSource(context, source)
                }.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(source.title), Modifier.weight(1f))
            RadioButton(source == selected, onClick = null)
        }
    }
}
