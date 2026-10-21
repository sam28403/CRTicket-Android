package cc.samlab.ticket.ui.railfan

import android.net.Uri
import androidx.compose.runtime.Composable
import cc.samlab.ticket.ui.trainlist.TrainListItem

@Composable
internal fun RoutingTrainCard(
    train: RoutingTrain,
    date: String,
    onNavigate: (String) -> Unit,
) {
    TrainListItem(
        row = train.row,
        highlighted = train.highlighted,
        enabled = train.clickable,
    ) {
        if (train.clickable) onNavigate(
            "/train?train=${Uri.encode(train.detailTrain)}&date=${Uri.encode(date)}&source=llt",
        )
    }
}
