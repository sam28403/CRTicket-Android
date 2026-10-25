package cc.samlab.ticket.update

import androidx.activity.ComponentActivity
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import cc.samlab.ticket.R
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.welcome.WelcomeScreen

internal fun ComponentActivity.updates(): UpdateViewModel =
    ViewModelProvider(this)[UpdateViewModel::class.java]

@Composable
internal fun UpdateHost(activity: ComponentActivity, dark: Boolean, pureBlack: Boolean) {
    val model = activity.updates()
    if (!BuildConfig.UPDATE_ENABLED) return
    LaunchedEffect(model) { model.onStartup() }
    LaunchedEffect(model.installDownloadId) {
        val id = model.installDownloadId ?: return@LaunchedEffect
        model.consumeInstallRequest()
        runCatching { activity.startActivity(UpdateInstallActivity.intent(activity, id)) }
            .onFailure {
                Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
    }
    val release = model.release ?: return
    Dialog(
        onDismissRequest = model::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MaterialTheme(colorScheme = homeColorScheme(activity, dark, pureBlack)) {
            WelcomeScreen(
                firstUse = false,
                changes = "# ${release.tag}\n\n" + release.notes.ifBlank {
                    stringResource(R.string.welcome_changes_fallback)
                },
                onExit = model::dismiss,
                releaseNotesOnly = true,
                onDownload = model::download,
                onComplete = model::dismiss,
            )
        }
    }
}
