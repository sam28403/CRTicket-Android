package cc.samlab.ticket.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressivePermissionDialog(
    prompt: ExpressivePrompt,
    dark: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean = false,
) {
    val context = LocalContext.current
    val colors = homeColorScheme(context, dark, pureBlack)
    MaterialExpressiveTheme(colorScheme = colors) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_notification_ticket),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(prompt.title) },
            text = {
                Text(
                    text = prompt.message,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(prompt.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = onDecline) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
        )
    }
}
