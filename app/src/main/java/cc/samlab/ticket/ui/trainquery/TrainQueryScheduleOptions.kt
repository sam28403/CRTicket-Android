package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun TrainQueryScheduleOptions(state: JSONObject, controller: NativeTrainQueryController) {
    val routes = state.optJSONArray("routeOptions")
    val zones = state.optJSONArray("timeZoneOptions")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (routes != null && routes.length() > 1) {
            ScheduleMenu(
                "train-query-route",
                routes.optString(state.optInt("selectedRoute")),
                List(routes.length()) { routes.getString(it) },
            ) { controller.dispatch("route", it.toString()) }
        }
        if (zones != null && zones.length() > 0) {
            val choices = List(zones.length()) { zones.getJSONObject(it) }
            val selected = choices.firstOrNull { it.optString("value") == state.optString("selectedTimeZone") }
            if (selected != null) ScheduleMenu(
                "train-query-time-zone",
                selected.optString("label"),
                choices.map { it.optString("label") },
            ) { controller.dispatch("timeZone", choices[it].optString("value")) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleMenu(tag: String, selected: String, labels: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) {
            Text(selected, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    onSelect(index)
                })
            }
        }
    }
}
