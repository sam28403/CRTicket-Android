package cc.samlab.ticket.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveCreditsScreen(
    dark: Boolean,
    pureBlack: Boolean,
    onBack: () -> Unit,
    onExternal: (String) -> Unit,
) {
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxSize().testTag("credits-screen")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                    Text(
                        stringResource(R.string.about_credits),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = projectTitleFont,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(280.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(projectCredits, key = { it.url }) { credit ->
                        Card(onClick = { onExternal(credit.url) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(credit.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        credit.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
