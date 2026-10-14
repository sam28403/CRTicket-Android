package cc.samlab.ticket.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.BuildConfig
import cc.samlab.ticket.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveAboutScreen(
    dark: Boolean,
    onBack: () -> Unit,
    onExternal: (String) -> Unit,
    pureBlack: Boolean = false,
) {
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_home_back),
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                    Text(stringResource(R.string.home_about), style = MaterialTheme.typography.titleLarge)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val availableHeight = maxHeight
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .heightIn(min = availableHeight)
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceAround,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AboutLogo()
                            Spacer(Modifier.height(40.dp))
                            Text(
                                stringResource(R.string.home_title),
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = projectTitleFont),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.about_description), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        val githubUrl = stringResource(R.string.about_github_url)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = { onExternal(githubUrl) },
                                shape = CircleShape,
                                color = colors.primaryContainer,
                                modifier = Modifier.size(70.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_home_github),
                                    contentDescription = stringResource(R.string.about_github),
                                    modifier = Modifier.padding(23.dp),
                                    tint = colors.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.about_github), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
