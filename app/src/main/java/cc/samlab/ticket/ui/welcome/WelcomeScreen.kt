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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.components.MarkdownText

@Composable
internal fun WelcomeScreen(
    firstUse: Boolean,
    changes: String,
    onExit: () -> Unit,
    onComplete: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    BackHandler { if (page == 1) page = 0 else onExit() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (page == 0) 0.5f else 1f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (page == 0) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                    ) {
                        WelcomeArtwork()
                        Text(
                            stringResource(if (firstUse) R.string.welcome_title else R.string.welcome_updated_title),
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = cc.samlab.ticket.ui.home.projectTitleFont,
                            fontWeight = FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            if (firstUse) stringResource(R.string.welcome_subtitle)
                            else stringResource(R.string.welcome_version, WelcomeState.version),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (firstUse) WelcomeFeatures()
                    }
                    Button(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.welcome_next))
                    }
                } else {
                    Text(
                        stringResource(if (firstUse) R.string.welcome_terms_title else R.string.welcome_changes_title),
                        style = MaterialTheme.typography.headlineLarge,
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
                        modifier = Modifier.fillMaxWidth(),
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
