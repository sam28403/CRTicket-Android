package cc.samlab.ticket.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun TimetableDeveloperGroup(controller: TimetableSettingsController, onImport: () -> Unit) {
    if (!BuildConfig.CI_BUILD) return
    val title = stringResource(R.string.llt_auto_update)
    SettingsGroup(stringResource(R.string.llt_settings_group)) {
        val canUpdate = cc.samlab.ticket.network.NetworkFeatures.enabled && !controller.busy
        Box(Modifier.alpha(if (canUpdate) 1f else 0.38f)) {
            SettingsRow(title, "", 0, 2, canUpdate, {
                controller.changeAutoUpdate(!controller.autoUpdate)
            }) {
                SettingsSwitch(controller.autoUpdate, canUpdate, title, controller::changeAutoUpdate)
            }
        }
        val enabled = !controller.busy
        Box(Modifier.alpha(if (enabled) 1f else 0.38f).testTag("llt-import-entry")) {
            SettingsRow(stringResource(R.string.llt_import_title), "", 1, 2, enabled, onImport)
        }
    }
}

@Composable
internal fun TimetableImportContent(controller: TimetableSettingsController, visible: Boolean) {
    if (!BuildConfig.CI_BUILD) return
    LaunchedEffect(visible) { if (visible) controller.refresh() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var undoJob by remember { mutableStateOf<Job?>(null) }
    val deleted = stringResource(R.string.speed_deleted)
    val undo = stringResource(R.string.speed_undo)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize()
                .testTag("llt-import-content"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 68.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "import-button") {
                val colors = ButtonDefaults.buttonColors()
                Button(
                    onClick = controller::chooseFile,
                    enabled = !controller.busy,
                    colors = colors.copy(
                        disabledContainerColor = colors.containerColor,
                        disabledContentColor = colors.contentColor,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)
                        .testTag("llt-import-button"),
                ) {
                    Text(stringResource(R.string.llt_import_button))
                }
            }
            timetableVersionItems(controller, !controller.busy && !controller.autoUpdate) { id ->
                controller.delete(id) {
                    undoJob?.cancel()
                    snackbar.currentSnackbarData?.dismiss()
                    undoJob = scope.launch {
                        var restored = false
                        try {
                            restored = snackbar.showSnackbar(deleted, undo, duration = SnackbarDuration.Short) ==
                                SnackbarResult.ActionPerformed
                            if (restored) controller.restore(id)
                        } finally {
                            if (!restored) controller.discardUndo(id)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
