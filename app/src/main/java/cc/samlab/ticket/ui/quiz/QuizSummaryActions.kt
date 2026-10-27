package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun QuizSummaryActions(busy: Boolean, onDone: () -> Unit, onReset: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().testTag("quiz-summary-actions"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onReset != null) OutlinedButton(
            onClick = onReset,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quiz-reset"),
        ) { Text(stringResource(R.string.quiz_reset)) }
        Button(
            onClick = onDone,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("quiz-done"),
        ) { Text(stringResource(R.string.quiz_done)) }
    }
}
