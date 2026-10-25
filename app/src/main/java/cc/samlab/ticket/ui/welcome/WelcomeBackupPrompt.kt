package cc.samlab.ticket.ui.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun WelcomeBackupPrompt(wide: Boolean, modifier: Modifier) {
    if (wide) {
        Row(
            modifier.testTag("welcome-backup-wide"),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(0.42f), contentAlignment = Alignment.Center) {
                WelcomeBackupArtwork()
            }
            BackupPromptText(Modifier.weight(0.58f).verticalScroll(rememberScrollState()))
        }
    } else {
        Column(
            modifier.verticalScroll(rememberScrollState()).testTag("welcome-backup-compact"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            WelcomeBackupArtwork()
            BackupPromptText(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BackupPromptText(modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            stringResource(R.string.welcome_backup_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.welcome_backup_message),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
