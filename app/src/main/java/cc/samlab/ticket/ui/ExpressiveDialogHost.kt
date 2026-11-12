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
    private val pending = ArrayDeque<ExpressivePrompt>()
    private var attached = false

    fun show(
        title: String? = null,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
        onDecline: (() -> Unit)? = null,
        onDismiss: () -> Unit = onDecline ?: {},
        owner: Any? = null,
        showIcon: Boolean = true,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onDismiss()
            return
        }
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
                            onDecline = current.onDecline?.let { action -> { respond(current, action) } },
                            onDismiss = { respond(current, current.onDismiss) },
                        )
                    }
                }
            }
            // Only the separate Compose Dialog window draws content and receives input.
            activity.addContentView(view, ViewGroup.LayoutParams(0, 0))
            attached = true
        }
        val next = ExpressivePrompt(
            title, message, confirmLabel, onConfirm, onDecline, onDismiss, owner, showIcon,
        )
        if (prompt == null) prompt = next else pending.addLast(next)
    }

    fun dismiss(owner: Any? = null) {
        if (owner == null) {
            pending.clear()
            prompt = null
        } else {
            pending.removeAll { it.owner === owner }
            if (prompt?.owner === owner) prompt = pending.removeFirstOrNull()
        }
    }

    fun close() {
        val remaining = listOfNotNull(prompt) + pending
        dismiss()
        remaining.forEach { it.onDismiss() }
    }

    private fun respond(current: ExpressivePrompt, action: () -> Unit) {
        if (prompt !== current) return
        prompt = pending.removeFirstOrNull()
        action()
    }
}

internal data class ExpressivePrompt(
    val title: String?,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
    val onDecline: (() -> Unit)?,
    val onDismiss: () -> Unit,
    val owner: Any?,
    val showIcon: Boolean,
)
