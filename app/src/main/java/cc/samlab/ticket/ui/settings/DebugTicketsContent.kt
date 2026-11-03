package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.history.HistoryRecordCard
import java.time.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DebugTicketsContent(controller: DebugTicketsController, visible: Boolean) {
    var choosingDate by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("debug-tickets-content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { choosingDate = true },
                    enabled = !controller.busy,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("debug-tickets-date"),
                    shape = MaterialTheme.shapes.large,
                ) { Text(controller.date) }
                OutlinedTextField(
                    value = controller.count,
                    onValueChange = { controller.count = it },
                    label = { Text(stringResource(R.string.debug_tickets_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !controller.busy,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).testTag("debug-tickets-count"),
                )
            }
        }
        item {
            Button(
                onClick = controller::generate,
                enabled = !controller.busy,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 56.dp)
                    .testTag("debug-tickets-generate"),
            ) {
                if (controller.busy) LoadingIndicator(Modifier.size(24.dp))
                else Text(stringResource(R.string.debug_tickets_generate))
            }
        }
        if (controller.rows.isEmpty()) {
            item {
                cc.samlab.ticket.ui.ExpressiveEmptyState(
                    message = stringResource(R.string.debug_tickets_empty),
                    visible = visible,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                        .testTag("debug-tickets-empty"),
                )
            }
        }
        items(controller.rows, key = { it.id }) { row ->
            Box(Modifier.widthIn(max = 640.dp)) {
                HistoryRecordCard(row, showActions = false) { _, _ -> }
            }
        }
    }
    if (choosingDate) {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(controller.date)
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !date.isBefore(today.minusDays(2)) && date.isBefore(today.plusDays(14))
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    state.selectedDateMillis?.let {
                        controller.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    choosingDate = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { choosingDate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state) }
    }
}
