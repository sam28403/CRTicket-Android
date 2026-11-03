package cc.samlab.ticket.ui.settings

import android.os.Handler
import android.os.Looper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R

@Composable
internal fun SettingsDiagnostics() {
    if (!BuildConfig.CI_BUILD) return
    var pending by remember { mutableStateOf<Int?>(null) }
    val options = listOf(R.string.failure_test_main, R.string.failure_test_worker, R.string.failure_test_anr)
    SettingsGroup(stringResource(R.string.failure_debug)) {
        options.forEachIndexed { index, title ->
            SettingsRow(stringResource(title), "", index, options.size, true, { pending = index })
        }
    }
    pending?.let { selected ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(options[selected])) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (selected) {
                        0 -> Handler(Looper.getMainLooper()).postDelayed({
                            error("CI simulated main thread crash")
                        }, 300)
                        1 -> Thread({ error("CI simulated background thread crash") }, "CI crash test").start()
                        2 -> Handler(Looper.getMainLooper()).postDelayed({ Thread.sleep(20_000) }, 300)
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
