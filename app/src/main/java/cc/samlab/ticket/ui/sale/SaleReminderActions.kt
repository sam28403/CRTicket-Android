package cc.samlab.ticket.ui.sale

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import cc.samlab.ticket.R
import cc.samlab.ticket.i18n.rememberAppText
import cc.samlab.ticket.notifications.NotificationAccess
import cc.samlab.ticket.sale.*
import org.json.JSONObject

internal data class SaleReminderActions(
    val rows: List<SaleReminder>,
    val toggle: (SaleReminder) -> Unit,
    val delete: (String) -> Unit,
    val restore: (SaleReminder) -> Boolean,
)

@Composable
internal fun rememberSaleReminderActions(now: java.time.Instant): SaleReminderActions {
    val context = LocalContext.current
    val text = rememberAppText()
    val store = remember { SaleReminderStore(context.applicationContext) }
    val channel = SaleReminderPreferences.channel(context)
    var revision by remember { mutableIntStateOf(0) }
    var pending by rememberSaveable { mutableStateOf("") }
    var waiting by rememberSaveable { mutableStateOf("") }
    var resume by remember { mutableStateOf<() -> Unit>({}) }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resume()
    }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) resume() else pending = ""
    }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val rows = remember(now.epochSecond, revision) { store.read() }
    fun finish() {
        if (pending.isBlank()) return
        val value = JSONObject(pending)
        val row = SaleReminder(value.getString("station"), value.getLong("saleAt"), value.getString("bureau"))
        pending = ""
        if (!NotificationAccess.enabled(context, channel) ||
            !SaleReminderAlarms.allowed(context) || !store.add(row)) {
            Toast.makeText(context, text(R.string.sale_reminder_unavailable), Toast.LENGTH_LONG).show()
        }
    }
    fun proceed(requestPermission: Boolean) {
        SaleReminderReceiver.createChannel(context)
        if (channel == SaleAlarmNotification.CHANNEL) SaleAlarmNotification.createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            if (requestPermission) {
                waiting = "notifications"
                notification.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else finish()
        } else if (!NotificationAccess.enabled(context, channel)) {
            if (requestPermission) {
                waiting = "notifications"
                settings.launch(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel))
            } else finish()
        } else if (!SaleReminderAlarms.allowed(context)) {
            if (requestPermission && Build.VERSION.SDK_INT >= 31) {
                waiting = "alarm"
                settings.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")))
            } else finish()
        } else finish()
    }
    SideEffect {
        resume = { proceed(waiting == "notifications" &&
            NotificationAccess.enabled(context, channel)) }
    }
    return SaleReminderActions(rows, { row ->
        if (rows.any { it.id == row.id }) store.remove(row.id)
        else {
            pending = JSONObject().put("station", row.station).put("saleAt", row.saleAt)
                .put("bureau", row.bureau).toString()
            proceed(true)
        }
    }, store::remove, store::add)
}
