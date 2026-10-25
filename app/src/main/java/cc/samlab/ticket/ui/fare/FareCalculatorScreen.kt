package cc.samlab.ticket.ui.fare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.ExpressiveEmptyState
import java.util.Locale

@Composable
internal fun FareCalculatorScreen(
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var train by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var air by rememberSaveable { mutableStateOf(FareAir.NEW) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var calculated by rememberSaveable { mutableStateOf(false) }
    val grade = OrdinaryFare.gradeForTrain(train)
    val validDistance = distance.matches(Regex("^[1-9]\\d*$")) &&
        (distance.toIntOrNull() ?: 0) in 1..OrdinaryFare.MAX_DISTANCE
    val keyboard = LocalSoftwareKeyboardController.current
    val calculate = {
        submitted = true
        calculated = grade != null && validDistance
        if (calculated) keyboard?.hide()
    }
    val result = remember(train, distance, air, calculated) {
        if (calculated && grade != null) OrdinaryFare.calculate(grade, air, distance.toInt()) else null
    }
    FarePage(R.string.fare_title, dark, pureBlack, embedded, onBack) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("fare-calculator"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Surface(shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            FareCalculatorFields(
                                train, distance, submitted, grade, validDistance,
                                onTrain = { train = it.uppercase(Locale.ROOT).take(5); calculated = false },
                                onDistance = { distance = it.take(5); calculated = false },
                                onCalculate = calculate,
                            )
                            FareChoices(
                                R.string.ticket_air_conditioning, FareAir.entries, air, { it.label }, "fare-air",
                            ) { air = it }
                            Button(onClick = calculate, modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 52.dp).testTag("fare-calculate")) {
                                Text(stringResource(R.string.fare_calculate))
                            }
                        }
                    }
                }
                item {
                    if (result != null) FareResults(result) else ExpressiveEmptyState(
                        message = stringResource(R.string.fare_empty), visible = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).testTag("fare-empty"),
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = { keyboard?.hide(); onNavigate("/price/list") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .testTag("fare-open-table"),
                    ) {
                        Text(stringResource(R.string.fare_open_table))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
                    }
                }
                item { FareNotice(calculator = true) }
            }
        }
    }
}
