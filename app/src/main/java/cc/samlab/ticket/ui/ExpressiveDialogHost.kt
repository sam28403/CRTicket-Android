package cc.samlab.ticket.ui

import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** A Compose dialog layer above the existing WebView, owned by the activity lifecycle. */
class ExpressiveDialogHost(private val activity: ComponentActivity) {
    var pureBlack by mutableStateOf(false)
    var dark by mutableStateOf(
        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    )
    private var prompt by mutableStateOf<ExpressivePrompt?>(null)
    private var attached = false

    fun show(
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
        onDecline: () -> Unit,
        onDismiss: () -> Unit = onDecline,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!attached) {
            val view = ComposeView(activity).apply {
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    prompt?.let { current ->
                        ExpressivePermissionDialog(
                            prompt = current,
                            dark = dark,
                            pureBlack = pureBlack,
                            onConfirm = { respond(current, current.onConfirm) },
                            onDecline = { respond(current, current.onDecline) },
                            onDismiss = { respond(current, current.onDismiss) },
                        )
                    }
                }
            }
            // Only the separate Compose Dialog window draws content and receives input.
            activity.addContentView(view, ViewGroup.LayoutParams(0, 0))
            attached = true
        }
        prompt = ExpressivePrompt(
            title, message, confirmLabel, onConfirm, onDecline, onDismiss
        )
    }

    fun dismiss() {
        prompt = null
    }

    private fun respond(current: ExpressivePrompt, action: () -> Unit) {
        if (prompt !== current) return
        prompt = null
        action()
    }
}

internal data class ExpressivePrompt(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
    val onDecline: () -> Unit,
    val onDismiss: () -> Unit,
)
