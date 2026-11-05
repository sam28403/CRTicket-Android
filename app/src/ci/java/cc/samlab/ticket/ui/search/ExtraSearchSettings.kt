package cc.samlab.ticket.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.settings.matchesDeveloperSearch

@Composable
internal fun extraSearchSettings(query: String, navigate: (String) -> Unit): List<SearchSetting> =
    if (matchesDeveloperSearch(query)) {
        listOf(SearchSetting("developer", stringResource(R.string.settings_developer), "") {
            navigate("/settings/developer")
        })
    } else emptyList()
