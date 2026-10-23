package cc.samlab.ticket.ui.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.settings.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveHelpScreen(
    location: String,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val topic = helpTopics.firstOrNull { it.route == location.substringBefore('?') }
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { active },
    )
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(
            modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.testTag(if (topic == null) "help-index" else "help-${topic.id}")) {
                SettingsHeader(
                    behavior, onBack, embedded && topic == null,
                    title = stringResource(topic?.title ?: R.string.home_help),
                )
                SettingsPanel(rememberScrollState(), "help-content", embedded) {
                    if (topic == null) {
                        SettingsGroup(stringResource(R.string.home_help_description), first = true) {
                            helpTopics.forEachIndexed { index, item ->
                                SettingsRow(
                                    stringResource(item.title), "", index, helpTopics.size, true,
                                    onClick = { onNavigate(item.route) },
                                )
                            }
                        }
                    } else {
                        SettingsGroup(stringResource(R.string.help_questions), first = true) {
                            HelpTextCard(stringResource(topic.question), stringResource(topic.answer))
                        }
                        SettingsGroup(stringResource(R.string.help_rules)) {
                            HelpTextCard(null, stringResource(topic.rules))
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun HelpTextCard(title: String?, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (title != null) Text(
                title,
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
