package cc.samlab.ticket.ui.trainquery

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.LocalContentColor
import cc.samlab.ticket.ui.home.projectTitleFont
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun TrainQueryStopCard(
    stop: JSONObject,
    index: Int,
    total: Int,
    highlight: QueryStopHighlight,
    dark: Boolean,
    onNavigate: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when (highlight) {
        QueryStopHighlight.IN_STATION -> if (dark) Color(0xFF154B2D) else Color(0xFFD1F0DC)
        QueryStopHighlight.UPCOMING -> colors.secondaryContainer
        QueryStopHighlight.NONE -> colors.surfaceContainerLowest
    }
    val foreground = when (highlight) {
        QueryStopHighlight.IN_STATION -> if (dark) Color(0xFFD9FFE5) else Color(0xFF103D21)
        QueryStopHighlight.UPCOMING -> colors.onSecondaryContainer
        QueryStopHighlight.NONE -> colors.onSurface
    }
    val status = when (highlight) {
        QueryStopHighlight.IN_STATION -> "停在站内"
        QueryStopHighlight.UPCOMING -> "前方到站"
        QueryStopHighlight.NONE -> ""
    }
    Surface(
        onClick = { onNavigate("/station?station=${Uri.encode(stop.optString("station"))}") },
        shape = RoundedCornerShape(20.dp),
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, if (status.isEmpty()) colors.outlineVariant else foreground.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().testTag("train-query-stop-$index")
            .semantics { stateDescription = status },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = foreground, contentColor = background) {
                    Text(
                        stop.optString("no", "${index + 1}"), Modifier.padding(10.dp),
                        fontFamily = projectTitleFont,
                        fontWeight = FontWeight.Normal,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    stop.optString("station"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(stop.optString("train"), style = MaterialTheme.typography.labelLarge)
                    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val day = stop.optInt("arrivalDay")
                StopDetail(
                    "到达时间",
                    if (index == 0) "始发站" else stop.text("arrival") + if (day > 0) " (+$day 天)" else "",
                    Modifier.weight(1f),
                )
                StopDetail("出发时间", if (index == total - 1) "终到站" else stop.text("departure"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StopDetail("停留时长", if (stop.isNull("stay")) "—" else "${stop.optString("stay")} 分钟", Modifier.weight(1f))
                StopDetail("里程", if (stop.isNull("mileage")) "暂无" else "${stop.optString("mileage")} km", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StopDetail(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.88f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun JSONObject.text(key: String) = if (isNull(key) || optString(key).isBlank()) "—" else optString(key)
