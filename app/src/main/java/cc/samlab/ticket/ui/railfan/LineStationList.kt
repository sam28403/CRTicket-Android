package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun LineStationList(line: String, stops: List<RailwayLineStop>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
        shape = RoundedCornerShape(24.dp), color = colors.surface,
    ) {
        Column {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(line, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.line_query_endpoints, stops.first().name, stops.last().name),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                if (maxWidth >= 600.dp) {
                    LineSnakeDiagram(stops, Modifier.fillMaxSize())
                } else LazyColumn(
                    Modifier.fillMaxSize().testTag("line-stations"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                ) {
                    itemsIndexed(stops) { index, stop ->
                        LineStationRow(stop, index == 0, index == stops.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineStationRow(stop: RailwayLineStop, first: Boolean, last: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 76.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(32.dp).fillMaxHeight()) {
            val x = size.width / 2
            val y = size.height / 2
            val radius = 8.dp.toPx()
            val gap = 3.dp.toPx()
            val stroke = 3.dp.toPx()
            listOf(x - gap, x + gap).forEach { rail ->
                if (!first) drawLine(colors.primary, Offset(rail, 0f), Offset(rail, y), stroke)
                if (!last) drawLine(colors.primary, Offset(rail, y), Offset(rail, size.height), stroke)
            }
            drawCircle(colors.surface, radius + 3.dp.toPx(), Offset(x, y))
            drawCircle(colors.primary, radius, Offset(x, y), style = Stroke(2.5.dp.toPx()))
        }
        Text(
            stop.name,
            Modifier.weight(1f).padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
        )
        Text(
            stop.kilometres?.let { stringResource(R.string.line_query_distance, it) }
                ?: stringResource(R.string.line_query_distance_unknown),
            maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant,
        )
    }
}
