package cc.samlab.ticket.ui.search

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.*
import cc.samlab.ticket.ui.trainlist.NativeTrainListController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun SearchVehicleAssignment(
    query: VehicleSearch,
    active: Boolean,
    onNavigate: (String) -> Unit,
    onFailure: () -> Unit,
    search: suspend (VehicleSearch) -> List<VehicleHistory> = VehicleQueryRepository::search,
    timetable: NativeTrainListController? = null,
    state: SearchVehicleState = remember(query) { SearchVehicleState() },
) {
    var latest by state.latest
    var loading by state.loading
    var error by state.error
    LaunchedEffect(query, active) {
        if (!active || state.completed) return@LaunchedEffect
        loading = true
        error = false
        try {
            delay(250)
            latest = search(query).firstOrNull()
            if (!query.byTrain) latest?.let { timetable?.requestVehicleRows(listOf(it)) }
            state.completed = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
            latest = null
            onFailure()
        } finally {
            loading = false
        }
    }
    if (error) return

    val route = "/railfan/vehicle?${if (query.byTrain) "train" else "vehicle"}=${Uri.encode(query.keyword)}"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { onNavigate(route) },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth().testTag("global-vehicle-search"),
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Train, null)
                Text(
                    if (query.byTrain) {
                        stringResource(R.string.vehicle_query_search_assignment, query.keyword)
                    } else "${stringResource(R.string.railfan_vehicle)} · ${query.keyword}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            latest != null -> {
                val row = latest!!
                Text(
                    if (query.byTrain) {
                        stringResource(R.string.vehicle_query_latest_assignment, row.date.take(16))
                    } else row.date.take(16),
                    Modifier.padding(start = 4.dp, top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.testTag("global-vehicle-latest")) {
                    if (query.byTrain) VehicleAssignmentCard(row) { onNavigate(route) }
                    else VehicleTrainHistoryCard(
                        history = row,
                        train = timetable?.vehicleRows?.get("${row.day}|${row.train}"),
                        onVehicle = { train ->
                            onNavigate("/railfan/vehicle?train=${Uri.encode(train)}")
                        },
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}
