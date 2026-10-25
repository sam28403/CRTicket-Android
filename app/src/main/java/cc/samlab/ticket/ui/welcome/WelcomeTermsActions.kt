package cc.samlab.ticket.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun WelcomeTermsActions(
    wide: Boolean,
    canAccept: Boolean,
    onAccept: () -> Unit,
    onExit: () -> Unit,
) {
    val acceptLabel = stringResource(
        if (canAccept) R.string.welcome_agree else R.string.welcome_read_all,
    )
    if (wide) {
        Row(
            Modifier.fillMaxWidth().testTag("welcome-terms-actions"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onAccept,
                enabled = canAccept,
                modifier = Modifier.weight(1f),
            ) {
                Text(acceptLabel)
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.welcome_decline))
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Button(onClick = onAccept, enabled = canAccept, modifier = Modifier.fillMaxWidth()) {
                Text(acceptLabel)
            }
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.welcome_decline))
            }
        }
    }
}
