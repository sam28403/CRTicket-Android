package cc.samlab.ticket.ui.search

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.railfan.LineQueryRepository
import cc.samlab.ticket.ui.railfan.matchingRailwayLines
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberLineSearchResults(query: String, enabled: Boolean): List<String> {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { LineQueryRepository(context) }
    val catalog by produceState(emptyList<String>(), repository, enabled) {
        value = emptyList()
        if (!enabled) return@produceState
        value = repository.catalog()
        try {
            value = repository.refreshCatalog()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Local search remains available without the upstream website.
        }
    }
    return remember(catalog, query, enabled) {
        if (enabled) matchingRailwayLines(catalog, query) else emptyList()
    }
}

internal fun LazyListScope.searchLineResultGroup(
    title: String,
    query: String,
    lines: List<String>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (lines.isEmpty()) return
    item(key = "lines-title") {
        Text(
            stringResource(R.string.search_group_count, title, lines.size),
            Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp).testTag("search-lines-title"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    item(key = "$query-lines-group") {
        val visible = if (expanded) lines else lines.take(3)
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("search-lines-group"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                visible.forEachIndexed { index, line ->
                    SearchLineRow(line) { onSelect(line) }
                    if (index < visible.lastIndex) HorizontalDivider(
                        Modifier.padding(start = 56.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                if (lines.size > 3) TextButton(
                    onClick = onExpand,
                    modifier = Modifier.fillMaxWidth().testTag("search-lines-expand"),
                ) {
                    Text(stringResource(if (expanded) R.string.search_collapse else R.string.search_expand))
                    Icon(Icons.Default.ExpandMore, null, Modifier.rotate(if (expanded) 180f else 0f))
                }
            }
        }
    }
}

@Composable
private fun SearchLineRow(line: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag("search-line:$line"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_home_lines), null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) { Text(line, style = MaterialTheme.typography.titleMedium) }
    }
}
