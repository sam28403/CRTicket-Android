package cc.samlab.ticket.ui.trainquery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.RoutingDisclaimer
import cc.samlab.ticket.ui.railfan.RoutingTrain
import cc.samlab.ticket.ui.railfan.RoutingTrainCard
import cc.samlab.ticket.ui.trainlist.parseTrainListRows
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun TrainQueryNextRouting(data: JSONObject, date: String, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val train = remember(data, context) {
        RoutingTrain(
            row = parseTrainListRows(context, JSONArray().put(data)).single(),
            detailTrain = data.optString("detailTrain"),
            highlighted = false,
            clickable = data.optBoolean("clickable"),
        )
    }
    Column(
        Modifier.fillMaxWidth().testTag("train-query-next-routing"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.train_query_next_routing),
            Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        RoutingTrainCard(train, date, onNavigate)
        RoutingDisclaimer()
    }
}
