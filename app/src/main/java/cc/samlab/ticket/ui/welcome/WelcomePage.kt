package cc.samlab.ticket.ui.welcome

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.MarkdownText

@Composable
internal fun WelcomePage(
    page: Int,
    firstUse: Boolean,
    wide: Boolean,
    changes: String,
    scroll: ScrollState,
    onNext: () -> Unit,
    onAccept: () -> Unit,
    onExit: () -> Unit,
    onDownload: (() -> Unit)?,
    onImportBackup: () -> Unit,
    onComplete: () -> Unit,
) {
    val wideTerms = wide && firstUse && page == 1
    val horizontalPadding = if (wide) 28.dp else 16.dp
    Box(Modifier.fillMaxSize().testTag("welcome-page-$page"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = if (wide) 1040.dp else 600.dp)
                .heightIn(max = if (wide && !wideTerms) 760.dp else Dp.Infinity)
                .fillMaxSize().padding(
                    start = horizontalPadding, end = horizontalPadding,
                    top = if (wide) 24.dp else 0.dp,
                    bottom = if (wide && !wideTerms) 64.dp else 8.dp,
                ).testTag("welcome-content"),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (firstUse) (page + 1) / 3f else if (page == 0) 0.5f else 1f },
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                page == 2 && firstUse -> {
                    WelcomeBackupPrompt(wide, Modifier.weight(1f).fillMaxWidth())
                    Button(
                        onClick = onImportBackup,
                        modifier = Modifier.fillMaxWidth(if (wide) 0.5f else 1f)
                            .align(Alignment.End).testTag("welcome-import-backup"),
                    ) { Text(stringResource(R.string.settings_import_backup)) }
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(if (wide) 0.5f else 1f)
                            .align(Alignment.End).testTag("welcome-skip-backup"),
                    ) { Text(stringResource(R.string.welcome_backup_skip)) }
                }
                page == 0 -> {
                    WelcomeIntro(firstUse, wide, Modifier.weight(1f).fillMaxWidth())
                    Button(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth(if (wide) 0.5f else 1f)
                            .align(Alignment.End).testTag("welcome-next"),
                    ) { Text(stringResource(R.string.welcome_next)) }
                }
                else -> {
                    Text(
                        stringResource(if (firstUse) R.string.welcome_terms_title else R.string.welcome_changes_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = if (firstUse) null else cc.samlab.ticket.ui.home.projectTitleFont,
                    )
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
                            .verticalScroll(scroll).padding(if (wide) 24.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (firstUse) Text(stringResource(R.string.welcome_terms),
                            style = MaterialTheme.typography.bodyLarge)
                        else MarkdownText(changes)
                        if (firstUse) Text(
                            stringResource(R.string.welcome_terms_end),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val canAccept = !scroll.canScrollForward && scroll.viewportSize > 0
                    when {
                        onDownload != null -> WelcomeUpdateActions(wide, onDownload, onComplete)
                        firstUse -> WelcomeTermsActions(wide, canAccept, onAccept, onExit)
                        else -> Button(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth(if (wide) 0.5f else 1f).align(Alignment.End),
                        ) { Text(stringResource(R.string.welcome_start)) }
                    }
                }
            }
            if (!wideTerms) Spacer(Modifier.height(8.dp))
        }
    }
}
