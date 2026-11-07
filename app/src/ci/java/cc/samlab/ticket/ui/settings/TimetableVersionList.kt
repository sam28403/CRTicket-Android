package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

internal fun LazyListScope.timetableVersionItems(
    controller: TimetableSettingsController,
    enabled: Boolean,
    onDelete: (String) -> Unit,
) {
    item(key = "official-version") {
        val officialSelected = controller.activeId.isEmpty()
        val officialVersion = controller.officialVersion.ifEmpty {
            if (officialSelected) controller.version else ""
        }
        val officialTitle = if (officialSelected) R.string.llt_current_version else R.string.llt_official_version
        SettingsGroup(stringResource(officialTitle)) {
            SettingsRow(
                officialVersion.ifEmpty { stringResource(R.string.llt_version_empty) },
                "", 0, 1, enabled && !officialSelected,
                { controller.select("") },
            ) {
                RadioButton(selected = officialSelected && officialVersion.isNotEmpty(), onClick = null)
            }
        }
    }
    val catalog = controller.catalog
    if (catalog.isEmpty()) return
    val versionsByDate = catalog.groupBy { it.date }
    item(key = "saved-versions-title") {
        SettingsGroup(stringResource(R.string.llt_saved_versions)) {}
    }
    itemsIndexed(catalog, key = { _, entry -> entry.id }) { index, entry ->
        val versions = versionsByDate.getValue(entry.date)
        val subtitle = if (versions.size > 1) {
            stringResource(R.string.llt_same_date_version, versions.indexOf(entry) + 1)
        } else ""
        val modifier = Modifier.animateItem()
            .clip(settingsRowShape(index, catalog.size))
            .testTag("llt-version:${entry.id}")
        SwipeTimetableVersion(modifier, enabled, { onDelete(entry.id) }) {
            val selected = entry.id == controller.activeId
            SettingsRow(entry.date, subtitle, index, catalog.size, enabled && !selected, {
                controller.select(entry.id)
            }) {
                RadioButton(selected = selected, onClick = null)
            }
        }
    }
}

@Composable
private fun SwipeTimetableVersion(
    modifier: Modifier,
    enabled: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val delete by rememberUpdatedState(onDelete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            delete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        },
    ) { content() }
}
