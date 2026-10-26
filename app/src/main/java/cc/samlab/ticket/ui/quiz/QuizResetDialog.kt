package cc.samlab.ticket.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun QuizResetDialog(onDismiss: () -> Unit, onReset: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quiz_reset)) },
        text = { Text(stringResource(R.string.quiz_reset_prompt)) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onReset(false) },
                    modifier = Modifier.fillMaxWidth().testTag("quiz-reset-same"),
                ) { Text(stringResource(R.string.quiz_reset_same)) }
                TextButton(
                    onClick = { onReset(true) },
                    modifier = Modifier.fillMaxWidth().testTag("quiz-reset-new"),
                ) { Text(stringResource(R.string.quiz_reset_new)) }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("quiz-reset-cancel"),
                ) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}
