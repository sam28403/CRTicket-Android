package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LineQueryScreen(
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { LineQueryRepository(context.applicationContext) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("") }
    var stops by remember { mutableStateOf<List<RailwayLineStop>>(emptyList()) }
    var loaded by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository) {
        lines = repository.catalog()
        try {
            lines = repository.refreshCatalog()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The bundled catalog remains searchable when the source is unavailable.
        }
    }
    LaunchedEffect(selected, retry) {
        stops = emptyList()
        loaded = ""
        error = false
        if (selected.isEmpty()) return@LaunchedEffect
        loading = true
        try {
            stops = repository.stops(selected)
            loaded = selected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        } finally {
            loading = false
        }
    }

    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.railfan_lines), fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded) FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                LineSearchField(
                    input = input,
                    lines = lines,
                    onEdit = { input = it },
                    onSelect = {
                        input = it
                        selected = it
                    },
                    onClear = {
                        input = ""
                        selected = ""
                    },
                )
                when {
                    selected.isEmpty() -> ExpressiveEmptyState(
                        stringResource(R.string.line_query_welcome), active,
                        Modifier.fillMaxWidth().weight(1f),
                    )
                    error -> VehicleQueryMessage(R.string.line_query_error) { retry++ }
                    loading || loaded != selected -> Box(
                        Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    else -> key(selected) {
                        LineStationList(selected, stops, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
