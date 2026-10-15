package cc.samlab.ticket.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import kotlin.math.roundToInt

@Composable
internal fun AvatarCropDialog(bitmap: Bitmap, controller: NativeSettingsController) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var x by remember(bitmap) { mutableFloatStateOf(0f) }
    var y by remember(bitmap) { mutableFloatStateOf(0f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    AlertDialog(
        onDismissRequest = { if (!controller.busy) controller.dismissCrop() },
        title = { Text(stringResource(R.string.settings_crop_avatar)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(192.dp).clip(CircleShape)) {
                    val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height) * zoom
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset(
                            ((size.width - width) / 2 + x * (width - size.width) / 2).roundToInt(),
                            ((size.height - height) / 2 + y * (height - size.height) / 2).roundToInt(),
                        ),
                        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                    )
                }
                Text(stringResource(R.string.settings_crop_zoom))
                Slider(zoom, { zoom = it }, valueRange = 1f..3f, enabled = !controller.busy)
                Text(stringResource(R.string.settings_crop_horizontal))
                Slider(x, { x = it }, valueRange = -1f..1f, enabled = !controller.busy)
                Text(stringResource(R.string.settings_crop_vertical))
                Slider(y, { y = it }, valueRange = -1f..1f, enabled = !controller.busy)
            }
        },
        confirmButton = {
            TextButton(enabled = !controller.busy, onClick = { controller.saveAvatar(zoom, x, y) }) {
                Text(stringResource(R.string.settings_save_avatar))
            }
        },
        dismissButton = {
            TextButton(enabled = !controller.busy, onClick = controller::dismissCrop) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
