package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun HistoryEditorContent(controller: NativeHistoryController, draft: HistoryDraft) {
    val textResources = rememberAppText()
    val enabled = !controller.ui.busy
    BoxWithConstraints(Modifier.fillMaxSize().testTag("history-editor")) {
        val wide = maxWidth >= 720.dp
        // WebViewLayout already insets the native host above the IME.
        // Applying imePadding here would subtract the keyboard height a second time.
        Column(
            Modifier.fillMaxSize().testTag("history-editor-scroll")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { JourneyFields(draft, controller.stations, enabled) }
                Column(Modifier.weight(1f)) {
                    TicketFields(draft, controller.seats, controller.airDisabledSeats, enabled)
                }
            } else {
                JourneyFields(draft, controller.stations, enabled)
                TicketFields(draft, controller.seats, controller.airDisabledSeats, enabled)
            }
            EditorSection(textResources(R.string.ticket_section_other)) {
                HistoryTextField(
                    draft,
                    "sellPlace",
                    textResources(R.string.ticket_sell_place),
                    textResources(R.string.ticket_sell_place_hint),
                    enabled = enabled,
                )
                HistoryTextField(
                    draft,
                    "gate",
                    textResources(R.string.ticket_gate),
                    textResources(R.string.ticket_gate_hint),
                    enabled = enabled,
                )
                HistoryChoiceField(draft, "theme", textResources(R.string.ticket_theme), controller.themes, enabled)
                HistoryTextField(
                    draft,
                    "message",
                    textResources(R.string.ticket_message),
                    multiline = true,
                    enabled = enabled,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { controller.ui.closeDetail() }, enabled = enabled) { Text(textResources(R.string.action_cancel)) }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = controller::submit, enabled = enabled,
                    modifier = Modifier.testTag("history-save"),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                ) { Text(if (enabled) textResources(R.string.history_save) else textResources(R.string.status_saving)) }
            }
        }
    }
}

@Composable
internal fun JourneyFields(draft: HistoryDraft, stations: List<HistoryStation>, enabled: Boolean) {
    val textResources = rememberAppText()
    EditorSection(textResources(R.string.ticket_section_journey)) {
        HistoryTextField(
            draft,
            "trainNo",
            textResources(R.string.ticket_train),
            textResources(R.string.ticket_train_hint),
            enabled = enabled,
        )
        HistoryStationField(draft, "from", textResources(R.string.ticket_from), stations, enabled)
        HistoryStationField(draft, "to", textResources(R.string.ticket_to), stations, enabled)
        HistoryDateTimeFields(draft, enabled)
        HistoryTextField(draft, "distance", textResources(R.string.ticket_distance), numeric = true, enabled = enabled)
    }
}

@Composable
internal fun TicketFields(
    draft: HistoryDraft,
    seats: List<HistoryChoice>,
    airDisabledSeats: Set<String>,
    enabled: Boolean,
) {
    val textResources = rememberAppText()
    EditorSection(textResources(R.string.ticket_section_details)) {
        HistoryTextField(
            draft,
            "number",
            textResources(R.string.ticket_number),
            textResources(R.string.ticket_number_hint),
            enabled = enabled,
        )
        HistoryTextField(draft, "price", textResources(R.string.ticket_price), numeric = true, enabled = enabled)
        HistoryChoiceField(draft, "seatType", textResources(R.string.ticket_seat_type), seats, enabled) { selected ->
            draft.fields["seatType"] = selected
            if (selected in airDisabledSeats) draft.conditioner = false
        }
        HistoryTextField(
            draft,
            "seatNo",
            textResources(R.string.ticket_seat_number),
            textResources(R.string.ticket_seat_number_hint),
            enabled = enabled,
        )
        HistorySwitch(textResources(R.string.ticket_use_credit), draft.credit, enabled, draft::changeCredit)
        HistorySwitch(
            textResources(R.string.ticket_air_conditioning), draft.conditioner,
            enabled && draft.value("seatType") !in airDisabledSeats,
        ) { draft.conditioner = it }
    }
}

@Composable
internal fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
