package cc.samlab.ticket.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.sale.*
import androidx.core.net.toUri

@Composable
internal fun SaleReminderSettingsContent(visible: Boolean, onOpenList: () -> Unit) {
    val context = LocalContext.current
    val permissionMessage = stringResource(R.string.sale_alarm_permission)
    var mode by remember { mutableStateOf(SaleReminderPreferences.mode(context)) }
    var warning by rememberSaveable { mutableStateOf(false) }
    var waiting by rememberSaveable { mutableStateOf("") }
    var next by remember { mutableStateOf<() -> Unit>({}) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = when (waiting) {
            "dnd" -> manager.isNotificationPolicyAccessGranted
            else -> Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
        }
        waiting = ""
        if (granted) next() else Toast.makeText(context,
            permissionMessage, Toast.LENGTH_LONG).show()
    }
    fun enableAlarm() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val intent = when {
            !manager.isNotificationPolicyAccessGranted -> {
                waiting = "dnd"
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
            Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent() -> {
                waiting = "fullscreen"
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    "package:${context.packageName}".toUri())
            }
            else -> null
        }
        if (intent != null) {
            try { permissions.launch(intent) } catch (_: android.content.ActivityNotFoundException) {
                waiting = ""
                Toast.makeText(context, permissionMessage,
                    Toast.LENGTH_LONG).show()
            }
        } else {
            SaleAlarmNotification.createChannel(context)
            mode = SaleReminderMode.ALARM
            SaleReminderPreferences.setMode(context, mode)
        }
    }
    SideEffect { next = ::enableAlarm }
    SettingsPanel(rememberScrollState(), "sale-reminder-settings") {
        SaleReminderIllustration(visible)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SaleReminderMode.entries.forEachIndexed { index, option ->
                Surface(shape = RoundedCornerShape(
                    topStart = if (index == 0) 28.dp else 4.dp,
                    topEnd = if (index == 0) 28.dp else 4.dp,
                    bottomStart = if (index == 1) 28.dp else 4.dp,
                    bottomEnd = if (index == 1) 28.dp else 4.dp,
                ),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .testTag("sale-mode-${option.name}")
                            .selectable(mode == option, role = Role.RadioButton) {
                                if (option == SaleReminderMode.ALARM) warning = true
                                else {
                                    mode = option
                                    SaleReminderPreferences.setMode(context, option)
                                }
                            }.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        RadioButton(selected = mode == option, onClick = null)
                        Text(stringResource(if (option == SaleReminderMode.ALARM)
                            R.string.sale_mode_alarm else R.string.sale_mode_notification),
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsRow(stringResource(R.string.sale_reminders), "", 0, 1, true, onOpenList)
    }
    if (warning) AlertDialog(
        onDismissRequest = { warning = false },
        title = { Text(stringResource(R.string.sale_mode_alarm)) },
        text = { Text(stringResource(R.string.sale_alarm_warning)) },
        confirmButton = { TextButton(onClick = { warning = false; enableAlarm() }) {
            Text(stringResource(R.string.action_confirm))
        } },
        dismissButton = { TextButton(onClick = { warning = false }) {
            Text(stringResource(R.string.action_cancel))
        } },
    )
}
