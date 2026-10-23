package cc.samlab.ticket.ui.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun WelcomeFeatures() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Outlined.ConfirmationNumber, R.string.home_ticket, Modifier.weight(1f))
            Feature(Icons.Outlined.Train, R.string.home_train, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Outlined.NotificationsActive, R.string.home_monitor, Modifier.weight(1f))
            Feature(Icons.Outlined.Route, R.string.home_data, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: Int, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        }
    }
}
