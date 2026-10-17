package cc.samlab.ticket.ui.trainquery

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun TrainQueryStopCard(stop: JSONObject, index: Int, total: Int, onNavigate: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val endpoint = index == 0 || index == total - 1
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (endpoint) colors.secondaryContainer else colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.primaryContainer) {
                    Text(stop.optString("no", "${index + 1}"), Modifier.padding(10.dp), color = colors.onPrimaryContainer)
                }
                Text(
                    stop.optString("station"),
                    Modifier.weight(1f).clickable {
                        onNavigate("/station?station=${Uri.encode(stop.optString("station"))}")
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(stop.optString("train"), style = MaterialTheme.typography.labelLarge)
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun JSONObject.text(key: String) = if (isNull(key) || optString(key).isBlank()) "—" else optString(key)
