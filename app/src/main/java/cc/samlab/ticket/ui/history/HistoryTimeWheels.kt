package cc.samlab.ticket.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.ui.components.PickerWheel
import cc.samlab.ticket.ui.components.PickerWheelState
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HistoryTimeWheels(hours: PickerWheelState, minutes: PickerWheelState) {
    val text = rememberAppText()
    val locale = LocalConfiguration.current.locales[0]
    val hourDescriptions = remember(locale, text) {
        val numbers = NumberFormat.getIntegerInstance(locale)
        List(24) { text(R.string.duration_compact_hours, numbers.format(it)) }
    }
    val minuteDescriptions = remember(locale, text) {
        val numbers = NumberFormat.getIntegerInstance(locale)
        List(60) { text(R.string.duration_compact_minutes, numbers.format(it)) }
    }
    val colors = ScrollFieldDefaults.colors()
    val wheelSize = Modifier.size(width = 100.dp, height = 120.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.testTag("history-time-scroll").semantics { isTraversalGroup = true }) {
            PickerWheel(
                state = hours,
                description = text(R.string.duration_compact_hours, "").trim(),
                modifier = wheelSize,
                colors = colors,
                optionDescription = { hourDescriptions[it] },
            )
            Box(
                modifier = Modifier.size(width = 16.dp, height = 120.dp).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ":",
                    modifier = Modifier.offset(y = (-4).dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayLarge.copy(
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    ),
                )
            }
            PickerWheel(
                state = minutes,
                description = text(R.string.duration_compact_minutes, "").trim(),
                modifier = wheelSize,
                colors = colors,
                optionDescription = { minuteDescriptions[it] },
            )
        }
    }
}
