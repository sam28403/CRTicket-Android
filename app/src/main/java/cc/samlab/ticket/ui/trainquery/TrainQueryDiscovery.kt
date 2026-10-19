package cc.samlab.ticket.ui.trainquery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.parseTrainListRows
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import java.time.LocalDate
import java.time.ZoneId

internal fun LazyGridScope.trainQueryDiscovery(
    controller: NativeTrainQueryController,
    state: JSONObject,
    onNavigate: (String) -> Unit,
) {
    fun open(code: String) {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        onNavigate("/train?train=${Uri.encode(code)}&date=$today")
    }
    val history = state.optJSONArray("history")
    item(key = "discovery-header", span = { GridItemSpan(maxLineSpan) }) {
        var displayedHistory by remember { mutableStateOf(history ?: JSONArray()) }
        SideEffect {
            if (history != null && history.length() > 0) displayedHistory = history
        }
        Column {
            AnimatedVisibility(
                visible = history != null && history.length() > 0,
                exit = shrinkVertically(tween(300)) + fadeOut(tween(180)),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("train-query-history"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(displayedHistory.length()) { index ->
                        val code = displayedHistory.getString(index)
                        OutlinedButton(onClick = { open(code) }) { Text(code) }
                    }
                    TextButton(
                        onClick = { controller.dispatch("clearHistory") },
                        modifier = Modifier.testTag("train-query-clear-history"),
                    ) { Text("清空") }
                }
            }
            Text(
                "今日随机推荐",
                Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp)
                    .testTag("train-query-recommendation-title"),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = projectTitleFont,
            )
        }
    }
    val recommendations = state.optJSONArray("recommendations")
    if (state.optBoolean("recommendationLoading")) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    } else if (state.optString("recommendationError").isNotEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { controller.dispatch("recommendations") }) {
                Text("推荐暂时无法加载，点击重试")
            }
        }
    } else if (recommendations == null || recommendations.length() == 0) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("今日暂无可推荐车次", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(recommendations.length()) { index ->
            val context = LocalContext.current
            val rows = remember(recommendations) { parseTrainListRows(context, recommendations) }
            val code = recommendations.getJSONObject(index).getString("queryTrain")
            TrainListItem(rows[index]) { open(code) }
        }
    }
}
