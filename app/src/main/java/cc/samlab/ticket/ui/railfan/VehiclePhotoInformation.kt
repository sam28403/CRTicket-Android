package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun VehiclePhotoInformation(info: VehiclePhotoInfo, compact: Boolean = false) {
    val uri = LocalUriHandler.current
    val unknown = stringResource(R.string.vehicle_photo_unknown)
    fun value(text: String) = text.takeUnless { it.isBlank() || it == "0" } ?: unknown
    val style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    Column(
        Modifier.testTag("vehicle-photo-information"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Text(stringResource(R.string.vehicle_photo_date, value(info.date)), style = style)
        Text(stringResource(R.string.vehicle_photo_allocation, value(info.allocation)), style = style)
        Text(stringResource(R.string.vehicle_photo_line, value(info.line)), style = style)
        Text(stringResource(R.string.vehicle_photo_city, value(info.city)), style = style)
        Text(stringResource(R.string.vehicle_photo_train, value(info.train)), style = style)
        Text(stringResource(R.string.vehicle_photo_author, value(info.author)), style = style)
        Text(stringResource(R.string.vehicle_photo_source), style = style)
        TextButton(onClick = { uri.openUri(info.sourceUrl) }) {
            Text(info.sourceUrl, style = if (compact) MaterialTheme.typography.bodySmall else style)
        }
    }
}
