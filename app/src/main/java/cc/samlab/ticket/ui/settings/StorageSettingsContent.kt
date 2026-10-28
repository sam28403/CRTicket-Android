package cc.samlab.ticket.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun StorageSettingsContent(controller: StorageController, visible: Boolean) {
    var pending by remember { mutableStateOf<Set<StorageKind>>(emptySet()) }
    val context = LocalContext.current
    val snapshot = controller.snapshot
    val kinds = StorageKind.entries.filter { it in snapshot.sizes }
    val format: (Long) -> String = { Formatter.formatShortFileSize(context, it) }
    LaunchedEffect(visible) {
        if (visible) controller.refresh()
        else pending = emptySet()
    }
    SettingsPanel(rememberScrollState(), "settings-storage") {
        StorageOverview(controller, format) {
            pending = kinds.filter {
                (snapshot.sizes[it] ?: 0) > 0 || it == StorageKind.TEST_SCREENSHOTS
            }.toSet()
        }
        SettingsGroup(stringResource(R.string.storage_categories)) {
            kinds.forEachIndexed { index, kind ->
                val bytes = snapshot.sizes[kind] ?: 0
                Box(Modifier.testTag("storage-category:${kind.name}")) {
                    SettingsRow(
                        stringResource(kind.title), format(bytes), index, kinds.size,
                        controller.loaded && !controller.busy &&
                            (bytes > 0 || kind == StorageKind.TEST_SCREENSHOTS),
                        { pending = setOf(kind) },
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.storage_clear))
                    }
                }
            }
        }
        SettingsGroup(stringResource(R.string.storage_saved)) {
            SettingsRow(
                stringResource(R.string.storage_personal), format(snapshot.savedBytes),
                0, 1, false, {}, trailing = {},
            )
        }
        if (controller.message.isNotEmpty()) Text(
            controller.message,
            Modifier.padding(12.dp).testTag("storage-result"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
    }
    if (visible && pending.isNotEmpty()) AlertDialog(
        onDismissRequest = { pending = emptySet() },
        icon = { Icon(Icons.Outlined.DeleteOutline, null) },
        title = { Text(stringResource(R.string.storage_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pending.joinToString("、") { context.getString(it.title) })
                Text(stringResource(
                    if (StorageKind.TIMETABLE in pending) R.string.storage_timetable_confirm
                    else R.string.storage_confirm_message,
                ))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { controller.clear(pending); pending = emptySet() },
                modifier = Modifier.testTag("storage-confirm"),
            ) { Text(stringResource(R.string.storage_clear)) }
        },
        dismissButton = {
            TextButton(onClick = { pending = emptySet() }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun StorageOverview(
    controller: StorageController,
    format: (Long) -> String,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Storage, null)
                Text(stringResource(R.string.storage_cleanable), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                if (controller.loaded) format(controller.snapshot.cleanable)
                else stringResource(R.string.status_loading),
                Modifier.testTag("storage-total"),
                style = MaterialTheme.typography.displayMedium,
            )
            if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                onClick = onClear,
                enabled = controller.loaded && !controller.busy &&
                    (controller.snapshot.cleanable > 0 ||
                        StorageKind.TEST_SCREENSHOTS in controller.snapshot.sizes),
                modifier = Modifier.fillMaxWidth().testTag("storage-clear-all"),
                contentPadding = PaddingValues(16.dp),
            ) { Text(stringResource(R.string.storage_clear_all)) }
        }
    }
}
