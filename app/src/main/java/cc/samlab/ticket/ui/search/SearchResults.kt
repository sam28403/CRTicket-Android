package cc.samlab.ticket.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R

internal fun <T> LazyListScope.searchResultGroup(
    key: String,
    title: String,
    query: String,
    rows: List<T>,
    expanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable (T, Int, Int) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "$key-title") {
        Text(
            stringResource(R.string.search_group_count, title, rows.size),
            Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                .testTag("search-$key-title"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    val visible = if (expanded) rows else rows.take(3)
    itemsIndexed(visible, key = { index, _ -> "$query-$key-$index" }) { index, row ->
        Box(Modifier.animateItem(
            fadeInSpec = tween(220),
            placementSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            fadeOutSpec = tween(160),
        )) { content(row, index, visible.size) }
    }
    if (rows.size > 3) {
        item(key = "$key-toggle") {
            val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "expand-arrow")
            TextButton(
                onClick = onExpand,
                modifier = Modifier.fillMaxWidth().animateItem().testTag("search-$key-expand"),
            ) {
                Text(stringResource(
                    if (expanded) R.string.search_collapse else R.string.search_expand,
                ))
                Icon(Icons.Default.ExpandMore, null, Modifier.rotate(angle))
            }
        }
    }
}
