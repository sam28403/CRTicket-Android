package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

internal data class HistoryStation(val name: String, val abbreviation: String, val pinyin: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryStationField(
    draft: HistoryDraft,
    key: String,
    label: String,
    stations: List<HistoryStation>,
    enabled: Boolean,
) {
    val textResources = rememberAppText()
    var expanded by remember { mutableStateOf(false) }
    val query = draft.value(key)
    val suggestions = remember(stations, query) {
        if (query.isBlank()) emptyList() else stations.asSequence().filter {
            it.name.contains(query) || it.abbreviation.contains(query, ignoreCase = true) ||
                it.pinyin.contains(query, ignoreCase = true)
        }.take(8).toList()
    }
    ExposedDropdownMenuBox(expanded && suggestions.isNotEmpty(), { expanded = it && enabled }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                draft.fields[key] = it
                expanded = true
            },
            label = { Text(label) },
            isError = draft.error(key) != null,
            supportingText = draft.error(key)?.let { message -> { Text(message) } },
            placeholder = { Text(textResources(R.string.station_search_hint)) },
            shape = MaterialTheme.shapes.large,
            enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .testTag("history-field-$key"),
        )
        ExposedDropdownMenu(expanded && suggestions.isNotEmpty(), { expanded = false }) {
            suggestions.forEach { station ->
                DropdownMenuItem(
                    text = { Text(station.name) },
                    onClick = {
                        draft.fields[key] = station.name
                        expanded = false
                    },
                )
            }
        }
    }
}
