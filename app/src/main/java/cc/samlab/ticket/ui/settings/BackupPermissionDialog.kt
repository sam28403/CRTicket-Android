package cc.samlab.ticket.ui.settings

import android.content.SharedPreferences
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cc.samlab.ticket.R
import cc.samlab.ticket.backup.BackupPermissionReview
import cc.samlab.ticket.i18n.rememberAppText

@Composable
internal fun BackupPermissionDialog(controller: NativeSettingsController) {
    val context = LocalContext.current
    val text = rememberAppText()
    val preferences = remember(context) { BackupPermissionReview.preferences(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences, lifecycle) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        val observer = LifecycleEventObserver { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    val missing = remember(revision) { BackupPermissionReview.missing(context) }
    if (missing.isEmpty()) return
    AlertDialog(
        onDismissRequest = { BackupPermissionReview.dismiss(context) },
        title = { Text(stringResource(R.string.settings_permissions)) },
        text = { Text(stringResource(R.string.backup_permissions_review,
            missing.joinToString("、") { text(it) })) },
        confirmButton = {
            TextButton(onClick = {
                BackupPermissionReview.dismiss(context)
                controller.permissions.refresh()
                controller.ui.openPermissions()
            }) { Text(stringResource(R.string.settings_permissions)) }
        },
        dismissButton = {
            TextButton(onClick = { BackupPermissionReview.dismiss(context) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
