package cc.samlab.ticket.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

@Composable
internal fun HomeDestinations(
    destinations: List<HomeDestination>,
    offset: Int,
    onNavigate: (String) -> Unit,
    selectedRoute: String = "",
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        destinations.forEachIndexed { index, destination ->
            val iconColors = homeIconColors(colors, index + offset)
            HomeEntry(
                title = stringResource(destination.title),
                subtitle = stringResource(destination.description),
                icon = destination.icon,
                shape = homeEntryShape(index, destinations.size),
                iconContainer = iconColors.container,
                iconTint = iconColors.foreground,
                selected = selectedRoute == destination.route,
                onClick = { onNavigate(destination.route) },
            )
        }
    }

}

@Composable
internal fun HomeUtilities(onNavigate: (String) -> Unit, selectedRoute: String = "") {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HomeEntry(
            title = stringResource(R.string.home_settings),
            subtitle = stringResource(R.string.home_settings_description),
            icon = R.drawable.ic_home_settings,
            shape = homeEntryShape(0, 2),
            iconContainer = homeIconColors(colors, 6).container,
            iconTint = homeIconColors(colors, 6).foreground,
            selected = selectedRoute == "/settings",
            onClick = { onNavigate("/settings") },
        )
        HomeEntry(
            title = stringResource(R.string.home_about),
            subtitle = stringResource(R.string.home_about_description),
            icon = R.drawable.ic_home_about,
            shape = homeEntryShape(1, 2),
            iconContainer = homeIconColors(colors, 7).container,
            iconTint = homeIconColors(colors, 7).foreground,
            selected = selectedRoute == "/about",
            onClick = { onNavigate("/about") },
        )
    }

}
