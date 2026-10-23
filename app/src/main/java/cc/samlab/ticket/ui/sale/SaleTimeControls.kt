package cc.samlab.ticket.ui.sale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaleTimeControls(
    input: String, stations: List<SaleStation>, sameCity: Boolean,
    onInput: (String) -> Unit, onSameCity: (Boolean) -> Unit, onSearch: () -> Unit,
) {
    val text = rememberAppText()
    var expanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = stations.any { it.name == input.trim() }
    val suggestions = remember(stations, input) {
        if (input.isBlank()) emptyList() else stations.filter {
            it.name.contains(input) || it.abbreviation.contains(input, true) ||
                it.pinyin.contains(input, true)
        }.take(8)
    }
    fun submit() { if (valid) { expanded = false; keyboard?.hide(); onSearch() } }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExposedDropdownMenuBox(
                expanded && suggestions.isNotEmpty(), { expanded = it }, Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    input, { onInput(it.take(30)); expanded = true },
                    placeholder = { Text(text(R.string.station_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("sale-query")
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(expanded && suggestions.isNotEmpty(), { expanded = false }) {
                    suggestions.forEach { station ->
                        DropdownMenuItem(text = { Text(station.name) }, onClick = {
                            onInput(station.name); expanded = false
                        })
                    }
                }
            }
            Button(onClick = { submit() }, enabled = valid, modifier = Modifier.testTag("sale-search")) {
                Text(text(R.string.sale_search))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text(R.string.sale_same_city), Modifier.weight(1f))
            Switch(sameCity, onSameCity, Modifier.testTag("sale-same-city"))
        }
    }
}
