package cc.samlab.ticket.ui.travel

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.samlab.ticket.R
import cc.samlab.ticket.ui.ExpressiveEmptyState
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont
import cc.samlab.ticket.ui.trainlist.TrainListRefreshIndicator

internal val TravelContentRevision = SemanticsPropertyKey<Int>("TravelContentRevision")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TravelScreen(
    location: String,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    cardLookup: TravelCardLookup,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    page: TravelPageState = viewModel(),
) {
    val context = LocalContext.current
    val largeScreen = embedded || LocalConfiguration.current.smallestScreenWidthDp >= 600
    val articlePage = location.substringBefore('?') == "/travel/article"
    val article = remember(location) {
        TravelRepository.article(Uri.parse(location).getQueryParameter("file"))
    }
    val refresh = { page.load(context.applicationContext, articlePage, article) }
    LaunchedEffect(page) {
        page.load(context.applicationContext, articlePage, article, initial = true)
    }
    val indexTitle = stringResource(R.string.travel_title)

    MaterialExpressiveTheme(colorScheme = homeColorScheme(context, dark, pureBlack)) {
        val surface = MaterialTheme.colorScheme.surfaceContainer
        Surface(Modifier.fillMaxSize(), color = surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            article?.title ?: indexTitle,
                            fontFamily = projectTitleFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (!embedded || articlePage) FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = refresh, enabled = !page.loading) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.station_refresh))
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
                )
                val pull = rememberPullToRefreshState()
                val refreshing = page.loading && page.result != null
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = refresh,
                    state = pull,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("travel-pull")
                        .semantics { this[TravelContentRevision] = page.revision },
                    indicator = {
                        TrainListRefreshIndicator(pull, refreshing, stringResource(R.string.refresh_loading))
                    },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        TravelResult(page.result, page.loading, refresh) { content ->
                            when (content) {
                                is TravelContent.Body -> TravelArticleBody(
                                    content.article, content.markdown, if (largeScreen) 600.dp else 400.dp,
                                    cardLookup, page.revision, onNavigate, page.articleState,
                                )
                                is TravelContent.Index -> if (content.articles.isEmpty()) {
                                    TravelEmptyState(R.string.search_no_results, page.loading, refresh)
                                } else {
                                    TravelArticleList(content.articles, onNavigate)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelArticleList(articles: List<TravelArticle>, onNavigate: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 248.dp),
        modifier = Modifier.fillMaxSize().testTag("travel-list"),
        contentPadding = travelPagePadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(articles, key = { it.file }) { article ->
            Surface(
                onClick = { onNavigate("/travel/article?file=${Uri.encode(article.file)}") },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth().testTag("travel-entry:${article.file}"),
            ) {
                ListItem(
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TravelEmptyState(message: Int, loading: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("travel-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveEmptyState(stringResource(message), visible = true)
        Button(onClick = onRefresh, enabled = !loading, modifier = Modifier.testTag("travel-empty-refresh")) {
            Text(stringResource(R.string.station_refresh))
        }
    }
}

@Composable
private fun <T> TravelResult(
    result: Result<T>?,
    loading: Boolean,
    onRefresh: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when {
        result == null -> CircularProgressIndicator(Modifier.padding(24.dp))
        result.isSuccess -> content(result.getOrThrow())
        else -> TravelEmptyState(R.string.train_list_network_error, loading, onRefresh)
    }
}
