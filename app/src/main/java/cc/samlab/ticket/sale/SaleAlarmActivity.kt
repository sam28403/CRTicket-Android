package cc.samlab.ticket.sale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import kotlinx.coroutines.delay

class SaleAlarmActivity : ComponentActivity() {
    private val saleAt get() = intent.getLongExtra("saleAt", 0)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (saleAt <= System.currentTimeMillis() || saleAt > System.currentTimeMillis() + 120_000) {
            finish()
            return
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialExpressiveTheme(colorScheme = homeColorScheme(
                this, cc.samlab.ticket.ui.ThemePreference.isDark(this),
                getSharedPreferences("android", MODE_PRIVATE).getBoolean("appearancePureBlack", false),
            )) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding()
                            .verticalScroll(rememberScrollState()).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
                    ) {
                        cc.samlab.ticket.ui.settings.SaleReminderIllustration(true)
                        Text(stringResource(R.string.settings_sale_reminders),
                            style = MaterialTheme.typography.headlineLarge)
                        Text(intent.getStringExtra("body").orEmpty(),
                            style = MaterialTheme.typography.titleLarge)
                        Button(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.sale_alarm_dismiss))
                        }
                    }
                }
            }
            BackHandler { dismiss() }
            LaunchedEffect(Unit) {
                repeat(120) {
                    delay(500)
                    if (!SaleAlarmService.active(this@SaleAlarmActivity, saleAt)) {
                        finish()
                        return@LaunchedEffect
                    }
                }
                dismiss()
            }
        }
    }

    private fun dismiss() {
        SaleAlarmService.dismiss(this, saleAt)
        finish()
    }
}

class SaleAlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val saleAt = intent.getLongExtra("saleAt", 0)
        SaleAlarmService.dismiss(context, saleAt)
    }
}
