package cc.samlab.ticket.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.network.NetworkFeatures
import cc.samlab.ticket.ui.navigation.ExpressiveRoutes
import cc.samlab.ticket.ui.railfan.RailfanPlaceholderScreen

@Composable
internal fun renderRestrictedFeature(
    location: String, dark: Boolean, pureBlack: Boolean, embedded: Boolean, onBack: () -> Unit,
): Boolean {
    val path = ExpressiveRoutes.path(location)
    if (NetworkFeatures.enabled || !NetworkFeatures.blocksRoute(path)) return false
    val title = when (path) {
        "/settings/map" -> R.string.settings_map
        "/travel/article" -> R.string.travel_title
        else -> (ticketDestinations + railfanDestinations).firstOrNull { it.route == path }?.title
            ?: R.string.network_features_enabled
    }
    RailfanPlaceholderScreen(title, dark, pureBlack, embedded && path != "/travel/article", onBack,
        stringResource(R.string.network_features_disabled))
    return true
}
