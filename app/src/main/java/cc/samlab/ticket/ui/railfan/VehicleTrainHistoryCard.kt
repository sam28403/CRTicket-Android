package cc.samlab.ticket.ui.railfan

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.trainlist.TrainListItem
import cc.samlab.ticket.ui.trainlist.TrainListRow

@Composable
internal fun VehicleTrainHistoryCard(
    history: VehicleHistory,
    train: TrainListRow?,
    onVehicle: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val missing = stringResource(R.string.display_unavailable)
    TrainListItem(
        row = train ?: TrainListRow(
            history.train, missing, missing, missing, missing, missing, missing,
            0, null, null, null,
        ),
        vehicle = history.displayVehicle,
        onVehicleClick = { onVehicle(history.train.substringBefore('/')) },
    ) {
        onNavigate(
            "/train?train=${Uri.encode(history.train.substringBefore('/'))}" +
                "&date=${Uri.encode(history.day)}&source=llt",
        )
    }
}
