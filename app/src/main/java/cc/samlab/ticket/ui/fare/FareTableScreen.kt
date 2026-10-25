package cc.samlab.ticket.ui.fare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.trainlist.BindTrainListScroll
import cc.samlab.ticket.ui.trainlist.TrainListScrollView

@Composable
internal fun FareTableScreen(dark: Boolean, pureBlack: Boolean, onBack: () -> Unit) {
    var grade by rememberSaveable { mutableStateOf(FareGrade.FAST) }
    var air by rememberSaveable { mutableStateOf(FareAir.NEW) }
    val list = rememberLazyListState()
    LaunchedEffect(grade, air) { list.scrollToItem(0) }
    AndroidView(
        factory = { TrainListScrollView(it).apply { id = R.id.expressive_fare_table_view } },
        modifier = Modifier.fillMaxSize(),
        onRelease = { it.disposeContent() },
        update = { scroll ->
            scroll.setContent {
                BindTrainListScroll(scroll.adapter, list)
                FarePage(R.string.fare_table, dark, pureBlack, false, onBack) {
                    val colors = MaterialTheme.colorScheme
                    SideEffect {
                        scroll.updateColor(colors.primary.toArgb(), colors.onSurfaceVariant.toArgb())
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FareSelector(R.string.fare_grade, FareGrade.entries, grade, { it.label },
                            "fare-grade", Modifier.weight(1f)) {
                            grade = it
                        }
                        FareSelector(R.string.ticket_air_conditioning, FareAir.entries, air, { it.label },
                            "fare-air", Modifier.weight(1f)) {
                            air = it
                        }
                    }
                    FareTableHeader()
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("fare-table")
                            .onGloballyPositioned { scroll.updateBounds(it.boundsInRoot()) },
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(OrdinaryFare.MAX_DISTANCE, key = { it + 1 }, contentType = { "fare-row" }) { index ->
                            val prices = remember(grade, air, index) {
                                OrdinaryFare.calculate(grade, air, index + 1)
                            }
                            FareTableRow(index + 1, prices)
                        }
                    }
                    FareNotice(calculator = false, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        },
    )
}
