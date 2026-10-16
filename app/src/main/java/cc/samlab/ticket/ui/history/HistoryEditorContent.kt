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

@Composable
internal fun HistoryEditorContent(controller: NativeHistoryController, draft: HistoryDraft) {
    val enabled = !controller.ui.busy
    BoxWithConstraints(Modifier.fillMaxSize().testTag("history-editor")) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { JourneyFields(controller, draft, enabled) }
                Column(Modifier.weight(1f)) { TicketFields(controller, draft, enabled) }
            } else {
                JourneyFields(controller, draft, enabled)
                TicketFields(controller, draft, enabled)
            }
            EditorSection("其他信息") {
                HistoryTextField(draft, "sellPlace", "售票地点", "例如：南京南站", enabled = enabled)
                HistoryTextField(draft, "gate", "检票 / 候车", "例如：1A / 一候", enabled = enabled)
                HistoryChoiceField(draft, "theme", "车票背景", controller.themes, enabled)
                HistoryTextField(draft, "message", "提示语", multiline = true, enabled = enabled)
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { controller.ui.closeDetail() }, enabled = enabled) { Text("取消") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = controller::submit, enabled = enabled,
                    modifier = Modifier.testTag("history-save"),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                ) { Text(if (enabled) "保存记录" else "正在保存…") }
            }
        }
    }
}

@Composable
private fun JourneyFields(controller: NativeHistoryController, draft: HistoryDraft, enabled: Boolean) {
    EditorSection("行程信息") {
        HistoryTextField(draft, "trainNo", "车次", "例如：G25 或 1461", enabled = enabled)
        HistoryStationField(draft, "from", "出发站", controller.stations, enabled)
        HistoryStationField(draft, "to", "到达站", controller.stations, enabled)
        HistoryDateTimeFields(draft, enabled)
        HistoryTextField(draft, "distance", "里程（公里）", numeric = true, enabled = enabled)
    }
}

@Composable
private fun TicketFields(controller: NativeHistoryController, draft: HistoryDraft, enabled: Boolean) {
    EditorSection("车票信息") {
        HistoryTextField(draft, "number", "票号 / 订单号", "例如：E351822734", enabled = enabled)
        HistoryTextField(draft, "price", "票价 / 积分", numeric = true, enabled = enabled)
        HistoryChoiceField(draft, "seatType", "席别", controller.seats, enabled) { selected ->
            draft.fields["seatType"] = selected
            if (selected in controller.airDisabledSeats) draft.conditioner = false
        }
        HistoryTextField(draft, "seatNo", "座位号", "例如：03车12A号", enabled = enabled)
        HistorySwitch("使用积分", draft.credit, enabled) { draft.credit = it }
        HistorySwitch(
            "空调", draft.conditioner,
            enabled && draft.value("seatType") !in controller.airDisabledSeats,
        ) { draft.conditioner = it }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
