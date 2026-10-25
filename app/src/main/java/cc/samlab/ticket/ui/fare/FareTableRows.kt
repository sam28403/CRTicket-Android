package cc.samlab.ticket.ui.fare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R

@Composable
internal fun FareTableHeader() {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(stringResource(R.string.fare_table_currency),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fullLabels = listOf(stringResource(R.string.ticket_distance)) +
                FareSeat.entries.map { stringResource(it.label) }
            val shortLabels = listOf(stringResource(R.string.fare_distance_short)) +
                FareSeat.entries.map { stringResource(it.shortLabel) }
            val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            val measurer = rememberTextMeasurer()
            val cellWidth = with(LocalDensity.current) { ((maxWidth - 24.dp) / 8 - 4.dp).toPx() }
            val compact = fullLabels.any {
                measurer.measure(it, style, softWrap = false, maxLines = 1).size.width > cellWidth
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (if (compact) shortLabels else fullLabels).forEach { label ->
                    BasicText(label, Modifier.weight(1f).padding(horizontal = 2.dp, vertical = 8.dp),
                        style = style.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 2,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = 0.5.sp,
                        ))
                }
            }
        }
    }
}

@Composable
internal fun FareTableRow(distance: Int, prices: List<FareQuote>) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(48.dp).testTag("fare-distance-$distance")
            .background(if (distance % 2 == 0) colors.surfaceContainerLow else colors.surfaceContainerLowest)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val values = listOf(distance.toString()) + prices.map { fareAmount(it.price) }
        values.forEachIndexed { index, value ->
            BasicText(value, Modifier.weight(1f).padding(horizontal = 1.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                    color = if (index == 0) colors.primary else colors.onSurface,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                ),
                softWrap = false, maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp, stepSize = 0.5.sp))
        }
    }
}
