package cc.samlab.ticket.ui.station

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StationBoardSearch(
    controller: StationBoardState,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        expanded = false
        keyboard?.hide()
        controller.request()
    }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded && controller.suggestions.isNotEmpty(),
            { expanded = it },
            Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = controller.station,
                onValueChange = { controller.editStation(it); expanded = true },
                placeholder = { Text("输入站名或拼音") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                enabled = !controller.loading,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag("station-query")
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(expanded && controller.suggestions.isNotEmpty(), { expanded = false }) {
                controller.suggestions.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { controller.selectStation(name); expanded = false },
                    )
                }
            }
        }
        Button(
            onClick = { submit() },
            enabled = !controller.loading,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.height(48.dp).testTag("station-submit"),
        ) { Text("查询") }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StationBoardTabs(
    selected: StationTab,
    modifier: Modifier = Modifier,
    onSelect: (StationTab) -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()).height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationTab.entries.forEachIndexed { index, tab ->
            ToggleButton(
                checked = tab == selected,
                onCheckedChange = { onSelect(tab) },
                modifier = Modifier.height(36.dp).testTag("station-tab-${tab.key}")
                    .semantics { role = Role.Tab; this.selected = tab == selected },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    StationTab.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) { Text(tab.title, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        }
    }
}
