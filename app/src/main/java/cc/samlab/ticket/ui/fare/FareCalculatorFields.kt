package cc.samlab.ticket.ui.fare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun FareCalculatorFields(
    train: String,
    distance: String,
    submitted: Boolean,
    grade: FareGrade?,
    validDistance: Boolean,
    onTrain: (String) -> Unit,
    onDistance: (String) -> Unit,
    onCalculate: () -> Unit,
) {
    val trainError = (submitted || train.isNotEmpty()) && grade == null
    val distanceError = (submitted || distance.isNotEmpty()) && !validDistance
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = train, onValueChange = onTrain, singleLine = true,
            label = { Text(stringResource(R.string.ticket_train)) },
            placeholder = { Text("Z1 / N1") },
            isError = trainError,
            supportingText = if (trainError || grade != null) { {
                Text(if (trainError) stringResource(R.string.fare_train_error)
                    else "${stringResource(R.string.fare_grade)}  ${stringResource(grade!!.label)}")
            } } else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).testTag("fare-train"),
        )
        OutlinedTextField(
            value = distance, onValueChange = onDistance, singleLine = true,
            label = { Text(stringResource(R.string.ticket_distance)) },
            isError = distanceError,
            supportingText = if (distanceError) { { Text(stringResource(R.string.fare_distance_error)) } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCalculate() }),
            modifier = Modifier.weight(1f).testTag("fare-distance"),
        )
    }
}
