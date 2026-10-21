package cc.samlab.ticket.ui.railfan

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RoutingQueryScreen(
    controller: RoutingQueryController,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    MaterialExpressiveTheme(colorScheme = homeColorScheme(LocalContext.current, dark, pureBlack)) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.railfan_routing), fontFamily = projectTitleFont) },
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
                VehicleSearchField(
                    input = controller.input,
                    invalid = controller.invalid,
                    onEdit = { controller.input = it },
                    onClear = controller::clear,
                    onSearch = controller::search,
                    label = R.string.routing_query_hint,
                    invalidMessage = R.string.routing_query_invalid,
                    placeholder = "G1 / D55",
                    tag = "routing-search",
                )
                when {
                    controller.loading -> Box(
                        Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    controller.error -> VehicleQueryMessage(R.string.routing_query_error, controller::search)
                    controller.submitted.isEmpty() || controller.groups.isEmpty() -> ExpressiveEmptyState(
                        message = stringResource(
                            if (controller.submitted.isEmpty()) R.string.routing_query_welcome
                            else R.string.routing_query_empty,
                        ),
                        visible = active,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("routing-empty"),
                    )
                    else -> RoutingQueryResults(controller, Modifier.weight(1f), onNavigate)
                }
            }
        }
    }
}
