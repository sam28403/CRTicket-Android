package cc.samlab.ticket.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun WelcomeUpdateActions(
    wide: Boolean,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpdateAction(R.string.update_download, onDownload, Modifier.weight(1f))
            UpdateAction(R.string.update_later, onLater, Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpdateAction(R.string.update_download, onDownload, Modifier.fillMaxWidth())
            UpdateAction(R.string.update_later, onLater, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UpdateAction(label: Int, onClick: () -> Unit, modifier: Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(stringResource(label))
    }
}
