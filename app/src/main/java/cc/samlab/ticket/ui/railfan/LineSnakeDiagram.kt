package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun LineSnakeDiagram(stops: List<RailwayLineStop>, modifier: Modifier = Modifier) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.testTag("line-snake")) {
        val columns = (maxWidth / (180.dp * scale)).toInt().coerceIn(2, 6)
        val rows = stops.chunked(columns)
        LazyColumn(
            Modifier.fillMaxSize().testTag("line-stations"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(rows.size) { row ->
                SnakeStationRow(rows[row], columns, row, row == rows.lastIndex, scale)
            }
        }
    }
}

@Composable
private fun SnakeStationRow(
    stops: List<RailwayLineStop>,
    columns: Int,
    row: Int,
    last: Boolean,
    scale: Float,
) {
    val colors = MaterialTheme.colorScheme
    val reverse = row % 2 == 1
    val labelHeight = 80.dp * scale
    val rowHeight = labelHeight + 32.dp + 64.dp * scale
    Box(Modifier.fillMaxWidth().height(rowHeight)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / columns
            val y = labelHeight.toPx() + 16.dp.toPx()
            fun x(index: Int) = cell * (if (reverse) columns - index - 0.5f else index + 0.5f)
            val start = x(0)
            val end = x(stops.lastIndex)
            val bend = 20.dp.toPx()
            val path = Path().apply {
                moveTo(start, y)
                lineTo(end, y)
                if (row > 0) {
                    val edge = if (reverse) size.width - 8.dp.toPx() else 8.dp.toPx()
                    val sign = if (reverse) -1 else 1
                    moveTo(edge, 0f)
                    lineTo(edge, y - bend)
                    quadraticTo(edge, y, edge + sign * bend, y)
                    lineTo(start, y)
                }
                if (!last) {
                    val edge = if (reverse) 8.dp.toPx() else size.width - 8.dp.toPx()
                    val sign = if (reverse) 1 else -1
                    moveTo(end, y)
                    lineTo(edge + sign * bend, y)
                    quadraticTo(edge, y, edge, y + bend)
                    lineTo(edge, size.height)
                }
            }
            drawPath(path, colors.primary, style = Stroke(8.dp.toPx()))
            drawPath(path, colors.surface, style = Stroke(2.dp.toPx()))
            stops.indices.forEach { index ->
                val center = Offset(x(index), y)
                drawCircle(colors.surface, 12.dp.toPx(), center)
                drawCircle(colors.primary, 8.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(columns) { column ->
                val index = if (reverse) columns - column - 1 else column
                val stop = stops.getOrNull(index)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().height(labelHeight).padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (stop != null) Text(
                            stop.name, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                    if (stop != null) Text(
                        stop.kilometres?.let { stringResource(R.string.line_query_distance, it) }
                            ?: stringResource(R.string.line_query_distance_unknown),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurfaceVariant, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}
