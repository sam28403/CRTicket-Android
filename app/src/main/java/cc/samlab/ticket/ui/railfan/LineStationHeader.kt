package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.samlab.ticket.R

@Composable
internal fun LineStationHeader(line: String, from: String, to: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            line,
            Modifier.weight(1f).testTag("line-title"),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold, color = colors.onSurface,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                stepSize = 0.5.sp,
            ),
        )
        BasicText(
            stringResource(R.string.line_query_endpoints, from, to),
            Modifier.weight(1f).testTag("line-endpoints"),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colors.onSurfaceVariant, textAlign = TextAlign.End,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                stepSize = 0.5.sp,
            ),
        )
    }
}
