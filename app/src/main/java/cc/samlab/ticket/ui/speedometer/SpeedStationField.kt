package cc.samlab.ticket.ui.speedometer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.ui.history.HistoryStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedStationField(
    value: String, change: (String) -> Unit, label: Int, stations: List<HistoryStation>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value, stations) {
        stations.filter { value.isNotBlank() && (it.name.contains(value) ||
            it.abbreviation.contains(value, true) || it.pinyin.contains(value, true)) }.take(8)
    }
    ExposedDropdownMenuBox(expanded && suggestions.isNotEmpty(), { expanded = it }, modifier) {
        OutlinedTextField(value, { change(it.take(50)); expanded = true },
            label = { Text(stringResource(label)) }, singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().testTag("speed-station-$label")
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
        ExposedDropdownMenu(expanded && suggestions.isNotEmpty(), { expanded = false }) {
            suggestions.forEach { station ->
                DropdownMenuItem(text = { Text(station.name) }, onClick = { change(station.name); expanded = false })
            }
        }
    }
}
