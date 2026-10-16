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
internal fun StationBoardSearch(controller: NativeStationBoardController) {
    var expanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        expanded = false
        keyboard?.hide()
        controller.request()
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            modifier = Modifier.height(56.dp).testTag("station-submit"),
        ) { Text("查询") }
    }
}

@Composable
internal fun StationBoardActions(
    total: Int,
    follow: Boolean,
    onFollow: (Boolean) -> Unit,
    onLocate: () -> Unit,
    map: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("共 $total 趟", style = MaterialTheme.typography.titleMedium)
            if (!map) FilledTonalButton(onClick = onLocate) { Text("定位到当前时间") }
        }
        if (!map) Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("跟随当前时间", style = MaterialTheme.typography.labelLarge)
            Switch(checked = follow, onCheckedChange = onFollow, Modifier.testTag("station-follow"))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StationBoardTabs(selected: StationTab, onSelect: (StationTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()).height(48.dp),
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
