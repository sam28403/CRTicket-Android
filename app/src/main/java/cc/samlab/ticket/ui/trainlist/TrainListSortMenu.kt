package cc.samlab.ticket.ui.trainlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun TrainListSortMenu(controller: NativeTrainListController) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("train-list-sort"),
        ) {
            Icon(
                painterResource(R.drawable.ic_filter_list),
                stringResource(R.string.train_list_sort),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp),
        ) {
            TrainListSort.entries.forEach { sort ->
                SortOption(stringResource(sort.label), controller.sort == sort) {
                    controller.changeSort(sort = sort)
                    expanded = false
                }
            }
            HorizontalDivider()
            for (descending in listOf(false, true)) {
                SortOption(
                    stringResource(
                        if (descending) R.string.train_list_sort_descending
                        else R.string.train_list_sort_ascending,
                    ),
                    controller.descending == descending,
                ) {
                    controller.changeSort(descending = descending)
                    expanded = false
                }
            }
        }
    }
}

@Composable
private fun SortOption(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        modifier = Modifier.semantics { selected = checked },
        trailingIcon = { if (checked) Icon(Icons.Default.Check, contentDescription = null) },
    )
}
