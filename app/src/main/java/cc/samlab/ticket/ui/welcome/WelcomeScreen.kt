package cc.samlab.ticket.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.ui.components.StepPageTransition

@Composable
internal fun WelcomeScreen(
    firstUse: Boolean,
    changes: String,
    onExit: () -> Unit,
    releaseNotesOnly: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onImportBackup: () -> Unit = {},
    onComplete: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(if (releaseNotesOnly) 1 else 0) }
    val scroll = rememberScrollState()
    BackHandler { if (page > 0 && !releaseNotesOnly) page-- else onExit() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 840.dp
            StepPageTransition(page, Modifier.fillMaxSize(), label = "welcome-page") { shown ->
                WelcomePage(
                    shown, firstUse, wide, changes, scroll,
                    onNext = { if (page == shown) page = 1 },
                    onAccept = { if (page == shown) page = 2 },
                    onExit = { if (page == shown) onExit() },
                    onDownload = onDownload?.let { action -> { if (page == shown) action() } },
                    onImportBackup = { if (page == shown) onImportBackup() },
                    onComplete = { if (page == shown) onComplete() },
                )
            }
        }
    }
}
