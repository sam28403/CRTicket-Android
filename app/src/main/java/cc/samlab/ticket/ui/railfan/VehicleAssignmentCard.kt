package cc.samlab.ticket.ui.railfan

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException

/** Both search entry points render the vehicle query's exact card. */
@Composable
internal fun VehicleAssignmentCard(row: VehicleHistory, onClick: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var loading by remember(row.vehicle) { mutableStateOf(true) }
    val image by produceState<ImageBitmap?>(null, row.vehicle) {
        value = null
        loading = true
        try {
            value = VehicleQueryRepository.image(context, row.modelKey, row.vehicle)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null
        } finally {
            loading = false
        }
    }
    VehicleHistoryCard(row, image, loading, onClick)
}
