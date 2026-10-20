package cc.samlab.ticket.ui.trainquery

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.home.projectTitleFont
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
    val textResources = rememberAppText()
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
        QueryStopHighlight.IN_STATION -> textResources(R.string.station_in_station)
        QueryStopHighlight.UPCOMING -> textResources(R.string.station_upcoming)
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
                    textResources(R.string.station_arrival_time),
                    if (index == 0) textResources(R.string.station_origin) else stop.text("arrival"),
                    Modifier.weight(1f),
                    day = if (index > 0 && !stop.isNull("arrival")) day else 0,
                )
                StopDetail(
                    textResources(R.string.station_departure_time),
                    if (index == total - 1) textResources(R.string.station_terminal) else stop.text("departure"),
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StopDetail(
                    textResources(R.string.station_stay_duration),
                    if (stop.isNull("stay")) "—" else textResources(R.string.duration_minutes, stop.optString("stay")),
                    Modifier.weight(1f),
                )
                StopDetail(
                    textResources(R.string.ticket_mileage),
                    if (stop.isNull("mileage")) textResources(R.string.status_unavailable)
                    else textResources(R.string.distance_km, stop.optString("mileage")),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StopDetail(label: String, value: String, modifier: Modifier, day: Int = 0) {
    val content = LocalContentColor.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.88f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            if (day > 0) Surface(
                shape = RoundedCornerShape(6.dp),
                color = content.copy(alpha = 0.12f),
                contentColor = content,
                border = BorderStroke(1.dp, content.copy(alpha = 0.3f)),
            ) {
                Text(
                    "+$day",
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun JSONObject.text(key: String) = if (isNull(key) || optString(key).isBlank()) "—" else optString(key)
