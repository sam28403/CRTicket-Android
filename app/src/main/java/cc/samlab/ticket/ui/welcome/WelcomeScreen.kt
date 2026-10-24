package cc.samlab.ticket.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.MarkdownText

@Composable
internal fun WelcomeScreen(
    firstUse: Boolean,
    changes: String,
    onExit: () -> Unit,
    releaseNotesOnly: Boolean = false,
    onComplete: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(if (releaseNotesOnly) 1 else 0) }
    val scroll = rememberScrollState()
    BackHandler { if (page == 1 && !releaseNotesOnly) page = 0 else onExit() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.safeContentPadding(), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 840.dp
            Column(
                Modifier.widthIn(max = if (wide) 1040.dp else 600.dp)
                    .heightIn(max = if (wide) 760.dp else androidx.compose.ui.unit.Dp.Infinity)
                    .fillMaxSize().padding(
                        start = 28.dp, end = 28.dp,
                        top = if (wide) 24.dp else 0.dp,
                        bottom = if (wide) 64.dp else 8.dp,
                    ).testTag("welcome-content"),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (page == 0) 0.5f else 1f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (page == 0) {
                    WelcomeIntro(firstUse, wide, Modifier.weight(1f).fillMaxWidth())
                    Button(
                        onClick = { page = 1 },
                        modifier = Modifier.fillMaxWidth(if (wide) 0.5f else 1f)
                            .align(Alignment.End).testTag("welcome-next"),
                    ) {
                        Text(stringResource(R.string.welcome_next))
                    }
                } else {
                    Text(
                        stringResource(if (firstUse) R.string.welcome_terms_title else R.string.welcome_changes_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = if (firstUse) null else cc.samlab.ticket.ui.home.projectTitleFont,
                    )
                    Column(
                        Modifier.weight(1f).fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraLarge)
                            .verticalScroll(scroll).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (firstUse) {
                            Text(
                                stringResource(R.string.welcome_terms),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            MarkdownText(changes)
                        }
                        if (firstUse) Text(
                            stringResource(R.string.welcome_terms_end),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val canAccept = !scroll.canScrollForward && scroll.viewportSize > 0
                    Button(
                        onClick = onComplete,
                        enabled = !firstUse || canAccept,
                        modifier = Modifier.fillMaxWidth(if (wide) 0.5f else 1f).align(Alignment.End),
                    ) {
                        Text(stringResource(
                            if (!firstUse) R.string.welcome_start
                            else if (canAccept) R.string.welcome_agree else R.string.welcome_read_all,
                        ))
                    }
                    if (firstUse) TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.welcome_decline))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
