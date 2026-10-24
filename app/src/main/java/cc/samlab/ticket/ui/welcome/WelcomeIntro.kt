package cc.samlab.ticket.ui.welcome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.appVersionLabel
import cc.samlab.ticket.ui.home.projectTitleFont

@Composable
internal fun WelcomeIntro(firstUse: Boolean, wide: Boolean, modifier: Modifier) {
    if (wide) {
        Row(
            modifier.testTag("welcome-intro-wide"),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(0.42f), contentAlignment = Alignment.Center) { WelcomeArtwork() }
            WelcomeIntroText(
                firstUse, Modifier.weight(0.58f).verticalScroll(rememberScrollState()),
            )
        }
    } else {
        Column(
            modifier.verticalScroll(rememberScrollState()).testTag("welcome-intro-compact"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            WelcomeArtwork()
            WelcomeIntroText(firstUse, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WelcomeIntroText(firstUse: Boolean, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            stringResource(if (firstUse) R.string.welcome_title else R.string.welcome_updated_title),
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = projectTitleFont,
            textAlign = TextAlign.Center,
        )
        Text(
            if (firstUse) stringResource(R.string.welcome_subtitle)
            else stringResource(R.string.welcome_version, appVersionLabel),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (firstUse) WelcomeFeatures()
    }
}
