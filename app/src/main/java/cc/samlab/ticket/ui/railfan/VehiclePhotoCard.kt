package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun VehiclePhotoCard(photo: VehiclePhoto, modifier: Modifier = Modifier) {
    var details by remember(photo) { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        if (maxWidth >= 720.dp) {
            Card(Modifier.fillMaxWidth().testTag("vehicle-photo")) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp).testTag("vehicle-photo-wide"),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    VehiclePhotoImage(photo, Modifier.weight(1.15f), maxHeight = 280)
                    Column(Modifier.weight(1f)) {
                        Text(photo.info.vehicle, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        VehiclePhotoInformation(photo.info, compact = true)
                    }
                }
            }
        } else {
            Card(onClick = { details = true }, modifier = Modifier.fillMaxWidth().testTag("vehicle-photo")) {
                VehiclePhotoImage(photo, Modifier.fillMaxWidth(), maxHeight = 240)
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(photo.info.vehicle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                VehiclePhotoInformation(photo.info)
            }
        },
        confirmButton = {
            TextButton(onClick = { details = false }) {
                Text(stringResource(R.string.vehicle_photo_close))
            }
        },
    )
}

@Composable
private fun VehiclePhotoImage(photo: VehiclePhoto, modifier: Modifier, maxHeight: Int) {
    Image(
        photo.image, photo.info.vehicle,
        modifier.heightIn(max = maxHeight.dp).aspectRatio(
            photo.image.width.toFloat() / photo.image.height,
        ),
        contentScale = ContentScale.Fit,
    )
}
